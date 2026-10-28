package com.euphony.player.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.euphony.player.model.Playlist
import com.euphony.player.model.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** List edits on playlists. Pure, so they are unit tested apart from file storage. */
object PlaylistOps {

    fun create(list: List<Playlist>, id: String, name: String): List<Playlist> =
        list + Playlist(id, name.trim().ifBlank { "New playlist" }, emptyList())

    fun rename(list: List<Playlist>, id: String, name: String): List<Playlist> =
        edit(list, id) { it.copy(name = name.trim().ifBlank { it.name }) }

    fun delete(list: List<Playlist>, id: String): List<Playlist> = list.filterNot { it.id == id }

    fun addTracks(list: List<Playlist>, id: String, trackIds: List<Long>): List<Playlist> =
        edit(list, id) { it.copy(trackIds = it.trackIds + trackIds) }

    fun removeAt(list: List<Playlist>, id: String, index: Int): List<Playlist> = edit(list, id) { p ->
        if (index !in p.trackIds.indices) p else p.copy(trackIds = p.trackIds.toMutableList().apply { removeAt(index) })
    }

    fun move(list: List<Playlist>, id: String, from: Int, to: Int): List<Playlist> = edit(list, id) { p ->
        if (from !in p.trackIds.indices || to !in p.trackIds.indices) {
            p
        } else {
            p.copy(trackIds = p.trackIds.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    private fun edit(list: List<Playlist>, id: String, change: (Playlist) -> Playlist) =
        list.map { if (it.id == id) change(it) else it }
}

/** Saves playlists as JSON in the app's private storage, written atomically. */
class PlaylistStore(context: Context) : PlaylistRepository {

    private val file = AtomicFile(File(context.filesDir, "playlists.json"))
    private val _playlists = MutableStateFlow(load())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    override fun create(name: String): Playlist {
        val id = UUID.randomUUID().toString()
        update(PlaylistOps.create(_playlists.value, id, name))
        return _playlists.value.first { it.id == id }
    }

    override fun rename(playlistId: String, name: String) = update(PlaylistOps.rename(_playlists.value, playlistId, name))

    override fun delete(playlistId: String) = update(PlaylistOps.delete(_playlists.value, playlistId))

    override fun addTracks(playlistId: String, trackIds: List<Long>) =
        update(PlaylistOps.addTracks(_playlists.value, playlistId, trackIds))

    override fun removeTrackAt(playlistId: String, index: Int) =
        update(PlaylistOps.removeAt(_playlists.value, playlistId, index))

    override fun move(playlistId: String, from: Int, to: Int) =
        update(PlaylistOps.move(_playlists.value, playlistId, from, to))

    private fun update(next: List<Playlist>) {
        _playlists.value = next
        save(next)
    }

    private fun load(): List<Playlist> = runCatching {
        if (!file.baseFile.exists()) return emptyList()
        val root = JSONArray(String(file.readFully(), Charsets.UTF_8))
        (0 until root.length()).map { i ->
            val o = root.getJSONObject(i)
            val ids = o.getJSONArray("tracks")
            Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
        }
    }.getOrElse {
        Log.w(TAG, "Could not read playlists", it)
        emptyList()
    }

    private fun save(list: List<Playlist>) {
        val json = JSONArray()
        list.forEach { p ->
            json.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("tracks", JSONArray().apply { p.trackIds.forEach { put(it) } }),
            )
        }
        val out = file.startWrite()
        try {
            out.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "Could not save playlists", e)
        }
    }

    private companion object {
        const val TAG = "PlaylistStore"
    }
}
