package com.euphony.player.data

import com.euphony.player.model.Track

/**
 * One audio file found in the music folder, with the tags read from it (null until read, or when the file has none).
 * [modified] and [size] tell whether a cached entry is still current.
 */
data class ScannedFile(
    val uri: String,
    val folderPath: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val modified: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long = 0,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val bitrate: Int = 0,
) {
    fun isSameFileAs(other: ScannedFile) = uri == other.uri && size == other.size && modified == other.modified

    fun toTrack(): Track {
        val folderName = LibraryGrouping.folderName(folderPath)
        // Untagged files are grouped as an album named after their folder.
        val albumTitle = album ?: folderName
        val albumKey = albumArtist?.lowercase() ?: folderPath.lowercase()
        return Track(
            id = FolderScan.stableId(uri),
            uri = uri,
            title = title ?: fileName.substringBeforeLast('.'),
            artist = artist ?: albumArtist ?: LibraryRepository.UNKNOWN_ARTIST,
            album = albumTitle,
            albumId = FolderScan.stableId("album|${albumTitle.lowercase()}|$albumKey"),
            durationMs = durationMs,
            trackNumber = trackNumber,
            year = year,
            folderPath = folderPath,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = size,
            bitrate = bitrate,
            dateAddedSec = modified / 1000,
        )
    }
}

/** Pure helpers for reading the music folder, kept apart from Android APIs so they are unit tested. */
object FolderScan {

    private val AUDIO_EXTENSIONS = setOf(
        "flac", "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "aif", "aiff", "mka", "amr", "3ga",
    )
    private val PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8", "pls", "cue")

    fun isAudio(fileName: String, mimeType: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext in PLAYLIST_EXTENSIONS) return false
        return ext in AUDIO_EXTENSIONS || mimeType.startsWith("audio/")
    }

    /** A positive 64-bit id derived from [key] (FNV-1a), stable across scans so playlists keep working. */
    fun stableId(key: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash and Long.MAX_VALUE
    }

    /** "Music/" for the chosen folder itself, "Music/Punjabi/" for a subfolder, matching MediaStore's style. */
    fun folderPath(rootName: String, subfolders: List<String>): String =
        (listOf(rootName) + subfolders).joinToString("/", postfix = "/")

    /** Track number as MediaStore stores it: disc × 1000 + track, from tags such as "3/12" and "2/2". */
    fun trackNumber(track: String?, disc: String?): Int {
        val t = track?.substringBefore('/')?.trim()?.toIntOrNull() ?: return 0
        val d = disc?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
        return if (d > 1) d * 1000 + t % 1000 else t
    }

    /** The year from tags such as "2019" or "2019-05-01T00:00:00Z"; 0 when absent. */
    fun year(raw: String?): Int = raw?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1000..9999 } ?: 0

    fun cleanTag(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
}
