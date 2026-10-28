package com.euphony.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanTest {

    private fun file(
        name: String,
        folder: String = "Music/Punjabi/",
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
    ) = ScannedFile(
        uri = "content://tree/document/primary%3A${folder}$name",
        folderPath = folder, fileName = name, mimeType = "audio/flac", size = 10, modified = 5_000,
        title = title, artist = artist, album = album, albumArtist = albumArtist,
    )

    @Test
    fun recognisesAudioByExtensionOrMimeAndSkipsPlaylists() {
        assertTrue(FolderScan.isAudio("Song.FLAC", ""))
        assertTrue(FolderScan.isAudio("track", "audio/mpeg"))
        assertFalse(FolderScan.isAudio("cover.jpg", "image/jpeg"))
        assertFalse(FolderScan.isAudio("mix.m3u", "audio/x-mpegurl"))
    }

    @Test
    fun stableIdIsPositiveAndRepeatable() {
        val a = FolderScan.stableId("content://x/a.flac")
        assertEquals(a, FolderScan.stableId("content://x/a.flac"))
        assertNotEquals(a, FolderScan.stableId("content://x/b.flac"))
        assertTrue(a >= 0)
    }

    @Test
    fun folderPathStartsAtTheChosenFolder() {
        assertEquals("Music/", FolderScan.folderPath("Music", emptyList()))
        assertEquals("Music/Punjabi/Live/", FolderScan.folderPath("Music", listOf("Punjabi", "Live")))
    }

    @Test
    fun parsesTrackDiscAndYearTags() {
        assertEquals(3, FolderScan.trackNumber("3/12", null))
        assertEquals(2003, FolderScan.trackNumber("3", "2/2"))
        assertEquals(0, FolderScan.trackNumber(null, "1"))
        assertEquals(2019, FolderScan.year("2019-05-01T00:00:00Z"))
        assertEquals(0, FolderScan.year("unknown"))
    }

    @Test
    fun untaggedFileUsesFileNameAndFolderAsAlbum() {
        val track = file("Tauba Tauba.flac").toTrack()
        assertEquals("Tauba Tauba", track.title)
        assertEquals("Punjabi", track.album)
        assertEquals(LibraryRepository.UNKNOWN_ARTIST, track.artist)
        assertEquals("Music/Punjabi/", track.folderPath)
    }

    @Test
    fun sameAlbumGroupsByAlbumArtistAcrossFolders() {
        val a = file("1.flac", folder = "Music/A/", album = "Live", albumArtist = "Band").toTrack()
        val b = file("2.flac", folder = "Music/B/", album = "Live", albumArtist = "Band").toTrack()
        val c = file("3.flac", folder = "Music/C/", album = "Live").toTrack()
        assertEquals(a.albumId, b.albumId)
        assertNotEquals(a.albumId, c.albumId)
    }

    @Test
    fun cachedEntryIsReusedOnlyWhenFileIsUnchanged() {
        val original = file("a.flac")
        assertTrue(original.copy(title = "Tagged").isSameFileAs(original))
        assertFalse(original.copy(size = 11).isSameFileAs(original))
        assertFalse(original.copy(modified = 6_000).isSameFileAs(original))
    }
}
