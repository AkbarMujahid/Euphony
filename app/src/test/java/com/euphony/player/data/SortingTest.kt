package com.euphony.player.data

import com.euphony.player.model.Album
import com.euphony.player.model.Folder
import com.euphony.player.model.GroupSort
import com.euphony.player.model.Track
import com.euphony.player.model.TrackSort
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {

    private fun track(id: Long, title: String, album: String = "Album", trackNumber: Int = 0, added: Long = 0, fileName: String = "$title.flac") = Track(
        id = id, uri = "content://tree/$id", title = title, artist = "Artist", album = album, albumId = 1,
        durationMs = 1, trackNumber = trackNumber, year = 0, folderPath = "Music/", fileName = fileName,
        mimeType = "audio/flac", sizeBytes = 1, bitrate = 0, dateAddedSec = added,
    )

    @Test
    fun byNameIgnoresCase() {
        val sorted = Sorting.tracks(listOf(track(1, "beta"), track(2, "Alpha"), track(3, "gamma")), TrackSort.NAME)
        assertEquals(listOf("Alpha", "beta", "gamma"), sorted.map { it.title })
    }

    @Test
    fun byDateAddedNewestFirst() {
        val sorted = Sorting.tracks(listOf(track(1, "Old", added = 1), track(2, "New", added = 9), track(3, "Mid", added = 5)), TrackSort.DATE_ADDED)
        assertEquals(listOf("New", "Mid", "Old"), sorted.map { it.title })
    }

    @Test
    fun byTrackNumberGroupsAlbumsAndKeepsDiscOrder() {
        val sorted = Sorting.tracks(
            listOf(
                track(1, "B2", album = "B", trackNumber = 2),
                track(2, "A disc 2", album = "A", trackNumber = 2001),
                track(3, "A1", album = "A", trackNumber = 1),
                track(4, "B1", album = "B", trackNumber = 1),
            ),
            TrackSort.TRACK_NUMBER,
        )
        assertEquals(listOf("A1", "A disc 2", "B1", "B2"), sorted.map { it.title })
    }

    @Test
    fun untaggedFilesKeepFileNameOrderByTrackNumber() {
        val sorted = Sorting.tracks(
            listOf(track(1, "Zebra", fileName = "02 Zebra.flac"), track(2, "Apple", fileName = "01 Apple.flac")),
            TrackSort.TRACK_NUMBER,
        )
        assertEquals(listOf("Apple", "Zebra"), sorted.map { it.title })
    }

    @Test
    fun albumsAndFoldersByNameOrNewest() {
        val tracks = listOf(track(1, "a", added = 10), track(2, "b", added = 50)).associateBy { it.id }
        val albums = listOf(Album(1, "zeta", "X", 0, listOf(1), ""), Album(2, "Alpha", "X", 0, listOf(2), ""))
        assertEquals(listOf("Alpha", "zeta"), Sorting.albums(albums, GroupSort.NAME) { 0 }.map { it.title })
        assertEquals(listOf("Alpha", "zeta"), Sorting.albums(albums, GroupSort.DATE_ADDED) { Sorting.newestOf(it.trackIds, tracks) }.map { it.title })

        val folders = listOf(Folder("Music/Rap/", "Rap", listOf(1)), Folder("Music/Lo-fi/", "Lo-fi", listOf(2)))
        assertEquals(listOf("Lo-fi", "Rap"), Sorting.folders(folders, GroupSort.NAME) { 0 }.map { it.name })
        assertEquals(listOf("Lo-fi", "Rap"), Sorting.folders(folders, GroupSort.DATE_ADDED) { Sorting.newestOf(it.trackIds, tracks) }.map { it.name })
        assertEquals(0L, Sorting.newestOf(listOf(42), tracks))
    }
}
