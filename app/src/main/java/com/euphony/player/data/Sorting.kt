package com.euphony.player.data

import com.euphony.player.model.Album
import com.euphony.player.model.Folder
import com.euphony.player.model.GroupSort
import com.euphony.player.model.Track
import com.euphony.player.model.TrackSort

/** Sort orders for songs, albums and folders. Pure, so it is unit tested. */
object Sorting {

    private val byTitle = compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName }

    fun tracks(tracks: List<Track>, sort: TrackSort): List<Track> = tracks.sortedWith(
        when (sort) {
            TrackSort.NAME -> byTitle
            // Newest first: that's what people look for after copying new music over.
            TrackSort.DATE_ADDED -> compareByDescending<Track> { it.dateAddedSec }.then(byTitle)
            // Album by album, in disc and track order. Untagged files (track 0) keep their file-name order.
            TrackSort.TRACK_NUMBER -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy { it.trackNumber }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        },
    )

    /** [newest] gives each album its most recently added track's date. */
    fun albums(albums: List<Album>, sort: GroupSort, newest: (Album) -> Long): List<Album> = when (sort) {
        GroupSort.NAME -> albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        GroupSort.DATE_ADDED -> albums.sortedWith(
            compareByDescending(newest).thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }

    fun folders(folders: List<Folder>, sort: GroupSort, newest: (Folder) -> Long): List<Folder> = when (sort) {
        // By path, so subfolders stay next to their parent.
        GroupSort.NAME -> folders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
        GroupSort.DATE_ADDED -> folders.sortedWith(
            compareByDescending(newest).thenBy(String.CASE_INSENSITIVE_ORDER) { it.path },
        )
    }

    /** Date of the most recently added track among [ids], for [albums] and [folders]. */
    fun newestOf(ids: List<Long>, byId: Map<Long, Track>): Long = ids.maxOfOrNull { byId[it]?.dateAddedSec ?: 0L } ?: 0L
}
