package com.euphony.player.data

import com.euphony.player.model.Album
import com.euphony.player.model.Artist
import com.euphony.player.model.Folder
import com.euphony.player.model.LibrarySnapshot
import com.euphony.player.model.Track

/** Builds the album, artist and folder views from a flat track list. Pure, so it is unit tested. */
object LibraryGrouping {

    const val UNKNOWN_ALBUM = "Unknown album"

    private val titleOrder = compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }

    // MediaStore stores disc in the thousands (2003 = disc 2, track 3), so a plain sort keeps discs in order.
    private val albumOrder = compareBy<Track> { it.trackNumber }.then(titleOrder)

    fun build(tracks: List<Track>) = LibrarySnapshot(
        tracks = tracks.sortedWith(titleOrder),
        albums = albums(tracks),
        artists = artists(tracks),
        folders = folders(tracks),
        isLoading = false,
    )

    fun albums(tracks: List<Track>): List<Album> = tracks
        .groupBy { it.albumId }
        .map { (id, group) ->
            val ordered = group.sortedWith(albumOrder)
            Album(
                id = id,
                title = group.first().album.ifBlank { UNKNOWN_ALBUM },
                artist = group.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
                year = group.maxOf { it.year },
                trackIds = ordered.map { it.id },
                coverTrackUri = ordered.first().uri,
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    fun artists(tracks: List<Track>): List<Artist> = tracks
        .groupBy { it.artist }
        .map { (name, group) ->
            val ordered = group.sortedWith(
                compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.then(albumOrder),
            )
            Artist(name = name, albumCount = group.distinctBy { it.albumId }.size, trackIds = ordered.map { it.id })
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun folders(tracks: List<Track>): List<Folder> = tracks
        .groupBy { it.folderPath }
        .map { (path, group) ->
            val ordered = group.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
            Folder(path = path, name = folderName(path), trackIds = ordered.map { it.id })
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })

    fun folderName(path: String): String =
        path.trimEnd('/').substringAfterLast('/').ifBlank { "Internal storage" }

    /** Track number as shown to people: drops the disc prefix MediaStore adds. */
    fun displayTrackNumber(raw: Int): Int = raw % 1000
}
