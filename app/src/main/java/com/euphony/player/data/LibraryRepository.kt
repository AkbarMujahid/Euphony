package com.euphony.player.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.AtomicFile
import android.util.Log
import androidx.core.content.edit
import com.euphony.player.model.LibrarySnapshot
import com.euphony.player.model.MusicFolder
import com.euphony.player.model.MusicLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads only the folder the person picked (and its subfolders) through the Storage Access Framework.
 * The app holds no storage permission, so nothing outside that folder is visible to it.
 * Tags are cached per file, so a rescan only reads files that are new or changed.
 */
class LibraryRepository(context: Context) : MusicLibrary {

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val cacheFile = AtomicFile(File(app.filesDir, "library_cache.json"))

    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    override val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val _folder = MutableStateFlow(savedFolder())
    override val folder: StateFlow<MusicFolder?> = _folder.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    // Tag reading is disk-bound; a few at a time is quicker than one, without swamping the provider.
    private val readers = Dispatchers.IO.limitedParallelism(4)

    override fun setFolder(treeUri: String): Boolean {
        val uri = Uri.parse(treeUri)
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "No lasting access to $treeUri", e)
            return false
        }
        // One folder at a time: give up access to any folder picked before.
        resolver.persistedUriPermissions.filter { it.uri != uri }.forEach {
            runCatching { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val name = folderName(uri)
        prefs.edit {
            putString(KEY_TREE, treeUri)
            putString(KEY_NAME, name)
        }
        cacheFile.delete()
        _snapshot.value = LibrarySnapshot(isLoading = true)
        _folder.value = MusicFolder(treeUri, name)
        scope.launch { refresh() }
        return true
    }

    override suspend fun refresh(): Unit = mutex.withLock {
        val folder = _folder.value
        if (folder == null) {
            _snapshot.value = LibrarySnapshot()
            return@withLock
        }
        _snapshot.update { it.copy(isLoading = true, scanDone = 0, scanTotal = 0) }
        val tracks = withContext(Dispatchers.IO) {
            runCatching { scan(folder) }.onFailure { Log.w(TAG, "Could not read ${folder.uri}", it) }.getOrNull()
        }
        if (tracks == null) {
            // The folder was deleted or access was revoked in Settings: ask for a folder again.
            if (!hasAccess(folder.uri)) forgetFolder()
            _snapshot.update { it.copy(isLoading = false, scanDone = 0, scanTotal = 0) }
            return@withLock
        }
        _snapshot.value = withContext(Dispatchers.Default) { LibraryGrouping.build(tracks.map { it.toTrack() }) }
    }

    private suspend fun scan(folder: MusicFolder): List<ScannedFile> {
        val found = listAudioFiles(Uri.parse(folder.uri), folder.name)
        val cached = loadCache()
        val total = found.size
        val done = AtomicInteger()
        _snapshot.update { it.copy(scanTotal = total) }
        val scanned = coroutineScope {
            found.map { file ->
                async(readers) {
                    val previous = cached[file.uri]
                    val result = if (previous != null && previous.isSameFileAs(file)) {
                        previous.copy(folderPath = file.folderPath, fileName = file.fileName)
                    } else {
                        readTags(file)
                    }
                    val n = done.incrementAndGet()
                    if (n % 10 == 0 || n == total) _snapshot.update { it.copy(scanDone = n, scanTotal = total) }
                    result
                }
            }.awaitAll()
        }
        saveCache(scanned)
        return scanned
    }

    /** Walks the folder tree; hidden files and folders (".name") are skipped. */
    private fun listAudioFiles(tree: Uri, rootName: String): List<ScannedFile> {
        val out = ArrayList<ScannedFile>()
        val pending = ArrayDeque<Pair<String, List<String>>>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to emptyList())
        while (pending.isNotEmpty()) {
            val (documentId, subfolders) = pending.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val cursor = resolver.query(children, CHILD_COLUMNS, null, null, null)
            if (cursor == null) {
                if (subfolders.isEmpty()) throw FileNotFoundException("Cannot list $tree")
                continue
            }
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    if (name.startsWith('.')) continue
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.add(id to subfolders + name)
                    } else if (FolderScan.isAudio(name, mime)) {
                        out += ScannedFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                            folderPath = FolderScan.folderPath(rootName, subfolders),
                            fileName = name,
                            mimeType = mime,
                            size = if (c.isNull(3)) 0 else c.getLong(3),
                            modified = if (c.isNull(4)) 0 else c.getLong(4),
                        )
                    }
                }
            }
        }
        return out
    }

    private fun readTags(file: ScannedFile): ScannedFile {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, Uri.parse(file.uri))
            fun tag(key: Int) = FolderScan.cleanTag(runCatching { retriever.extractMetadata(key) }.getOrNull())
            file.copy(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                trackNumber = FolderScan.trackNumber(
                    tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                ),
                year = FolderScan.year(tag(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)),
                bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
            )
        } catch (e: Exception) {
            // Unreadable tags: still list the file under its name.
            Log.w(TAG, "Could not read tags of ${file.fileName}", e)
            file
        } finally {
            retriever.release()
        }
    }

    private fun savedFolder(): MusicFolder? {
        val tree = prefs.getString(KEY_TREE, null) ?: return null
        if (!hasAccess(tree)) return null
        return MusicFolder(tree, prefs.getString(KEY_NAME, null) ?: "Music")
    }

    private fun hasAccess(treeUri: String): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == treeUri && it.isReadPermission }

    private fun forgetFolder() {
        prefs.edit { clear() }
        cacheFile.delete()
        _folder.value = null
    }

    private fun folderName(tree: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(tree)
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        val queried = runCatching {
            resolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        // Document ids look like "primary:Music/Songs"; fall back to the last path segment.
        return queried?.takeIf { it.isNotBlank() } ?: documentId.substringAfter(':').substringAfterLast('/').ifBlank { "Music" }
    }

    private fun loadCache(): Map<String, ScannedFile> = runCatching {
        if (!cacheFile.baseFile.exists()) return emptyMap()
        val array = JSONArray(String(cacheFile.readFully(), Charsets.UTF_8))
        (0 until array.length()).associate { i ->
            val o = array.getJSONObject(i)
            val file = ScannedFile(
                uri = o.getString("uri"),
                folderPath = o.optString("folder"),
                fileName = o.optString("file"),
                mimeType = o.optString("mime"),
                size = o.optLong("size"),
                modified = o.optLong("modified"),
                title = o.optStringOrNull("title"),
                artist = o.optStringOrNull("artist"),
                album = o.optStringOrNull("album"),
                albumArtist = o.optStringOrNull("albumArtist"),
                durationMs = o.optLong("duration"),
                trackNumber = o.optInt("track"),
                year = o.optInt("year"),
                bitrate = o.optInt("bitrate"),
            )
            file.uri to file
        }
    }.getOrElse {
        Log.w(TAG, "Ignoring unreadable library cache", it)
        emptyMap()
    }

    private fun saveCache(files: List<ScannedFile>) {
        val array = JSONArray()
        files.forEach { f ->
            array.put(
                JSONObject()
                    .put("uri", f.uri)
                    .put("folder", f.folderPath)
                    .put("file", f.fileName)
                    .put("mime", f.mimeType)
                    .put("size", f.size)
                    .put("modified", f.modified)
                    .putOpt("title", f.title)
                    .putOpt("artist", f.artist)
                    .putOpt("album", f.album)
                    .putOpt("albumArtist", f.albumArtist)
                    .put("duration", f.durationMs)
                    .put("track", f.trackNumber)
                    .put("year", f.year)
                    .put("bitrate", f.bitrate),
            )
        }
        val out = cacheFile.startWrite()
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            cacheFile.finishWrite(out)
        } catch (e: Exception) {
            cacheFile.failWrite(out)
            Log.w(TAG, "Could not save library cache", e)
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        private const val TAG = "LibraryRepository"
        private const val KEY_TREE = "tree_uri"
        private const val KEY_NAME = "folder_name"
        private val CHILD_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
