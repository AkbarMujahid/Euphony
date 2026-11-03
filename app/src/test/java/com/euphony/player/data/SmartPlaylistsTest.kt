package com.euphony.player.data

import com.euphony.player.model.SmartList
import com.euphony.player.model.Track
import com.euphony.player.model.TrackStats
import com.euphony.player.model.Usage
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlaylistsTest {

    private fun track(id: Long, title: String, added: Long = 0) = Track(
        id = id, uri = "content://tree/$id", title = title, artist = "Artist", album = "Album", albumId = 1,
        durationMs = 180_000, trackNumber = 0, year = 0, folderPath = "Music/", fileName = "$title.flac",
        mimeType = "audio/flac", sizeBytes = 1, bitrate = 0, dateAddedSec = added,
    )

    private val tracks = listOf(track(1, "One", added = 100), track(2, "Two", added = 300), track(3, "Three", added = 200))

    @Test
    fun favoritesNewestFirst() {
        val usage = Usage(favorites = mapOf(1L to 10L, 3L to 20L))
        assertEquals(listOf(3L, 1L), SmartPlaylists.select(SmartList.FAVORITES, tracks, usage).map { it.id })
    }

    @Test
    fun recentlyAddedNewestFirst() {
        assertEquals(listOf(2L, 3L, 1L), SmartPlaylists.select(SmartList.RECENTLY_ADDED, tracks, Usage()).map { it.id })
        assertEquals(listOf(2L), SmartPlaylists.select(SmartList.RECENTLY_ADDED, tracks, Usage(), limit = 1).map { it.id })
    }

    @Test
    fun recentlyPlayedOnlyListsPlayedSongs() {
        val usage = Usage(plays = mapOf(1L to TrackStats(5, 1_000), 2L to TrackStats(1, 2_000)))
        assertEquals(listOf(2L, 1L), SmartPlaylists.select(SmartList.RECENTLY_PLAYED, tracks, usage).map { it.id })
    }

    @Test
    fun mostPlayedByCountThenLatest() {
        val usage = Usage(
            plays = mapOf(1L to TrackStats(3, 1_000), 2L to TrackStats(3, 5_000), 3L to TrackStats(7, 100)),
        )
        assertEquals(listOf(3L, 2L, 1L), SmartPlaylists.select(SmartList.MOST_PLAYED, tracks, usage).map { it.id })
    }

    @Test
    fun songsNoLongerInTheFolderAreLeftOut() {
        val usage = Usage(favorites = mapOf(99L to 1L), plays = mapOf(99L to TrackStats(4, 1)))
        assertEquals(emptyList<Track>(), SmartPlaylists.select(SmartList.FAVORITES, tracks, usage))
        assertEquals(emptyList<Track>(), SmartPlaylists.select(SmartList.MOST_PLAYED, tracks, usage))
    }

    @Test
    fun aSongCountsAfterHalfItsLengthOrThirtySeconds() {
        assertEquals(30_000L, SmartPlaylists.playThresholdMs(240_000))
        assertEquals(10_000L, SmartPlaylists.playThresholdMs(20_000))
        assertEquals(30_000L, SmartPlaylists.playThresholdMs(0))
    }
}
