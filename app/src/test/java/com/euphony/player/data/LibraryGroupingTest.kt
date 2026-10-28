package com.euphony.player.data

import com.euphony.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGroupingTest {

    private fun track(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumId: Long = 1,
        trackNumber: Int = 0,
        folder: String = "Music/",
        fileName: String = "$title.flac",
    ) = Track(
        id = id, uri = "content://media/external/audio/media/$id", title = title, artist = artist,
        album = album, albumId = albumId, durationMs = 180_000, trackNumber = trackNumber, year = 2020,
        folderPath = folder, fileName = fileName, mimeType = "audio/flac", sizeBytes = 1, bitrate = 0,
        dateAddedSec = 0,
    )

    @Test
    fun tracksSortByTitleIgnoringCase() {
        val snapshot = LibraryGrouping.build(listOf(track(1, "beta"), track(2, "Alpha"), track(3, "Gamma")))
        assertEquals(listOf("Alpha", "beta", "Gamma"), snapshot.tracks.map { it.title })
    }

    @Test
    fun albumTracksFollowDiscThenTrackNumber() {
        val album = LibraryGrouping.albums(
            listOf(track(1, "Disc2 First", trackNumber = 2001), track(2, "Second", trackNumber = 2), track(3, "First", trackNumber = 1)),
        ).single()
        assertEquals(listOf(3L, 2L, 1L), album.trackIds)
        assertEquals("content://media/external/audio/media/3", album.coverTrackUri)
    }

    @Test
    fun albumArtistIsTheMostCommonArtist() {
        val album = LibraryGrouping.albums(
            listOf(track(1, "A", artist = "Guest"), track(2, "B", artist = "Main"), track(3, "C", artist = "Main")),
        ).single()
        assertEquals("Main", album.artist)
    }

    @Test
    fun blankAlbumNameGetsPlaceholder() {
        assertEquals(LibraryGrouping.UNKNOWN_ALBUM, LibraryGrouping.albums(listOf(track(1, "A", album = ""))).single().title)
    }

    @Test
    fun artistsCountDistinctAlbums() {
        val artist = LibraryGrouping.artists(
            listOf(track(1, "A", albumId = 1), track(2, "B", albumId = 1), track(3, "C", albumId = 2)),
        ).single()
        assertEquals(2, artist.albumCount)
        assertEquals(3, artist.trackIds.size)
    }

    @Test
    fun foldersGroupByPathAndNameFromLastSegment() {
        val folders = LibraryGrouping.folders(
            listOf(track(1, "A", folder = "Music/Jazz/"), track(2, "B", folder = "Music/Rock/"), track(3, "C", folder = "Music/Jazz/")),
        )
        assertEquals(listOf("Jazz", "Rock"), folders.map { it.name })
        assertEquals(2, folders.first().trackIds.size)
        assertEquals("Internal storage", LibraryGrouping.folderName(""))
    }

    @Test
    fun displayTrackNumberDropsDiscPrefix() {
        assertEquals(3, LibraryGrouping.displayTrackNumber(2003))
        assertEquals(7, LibraryGrouping.displayTrackNumber(7))
    }
}
