package com.euphony.player.data

import com.euphony.player.model.SmartList
import com.euphony.player.model.Track
import com.euphony.player.model.Usage

/** The automatic lists on the Playlists tab, built from local play counts. Pure, so it is unit tested. */
object SmartPlaylists {

    const val LIMIT = 100

    fun select(list: SmartList, tracks: List<Track>, usage: Usage, limit: Int = LIMIT): List<Track> = when (list) {
        // Newest favorite first.
        SmartList.FAVORITES -> tracks
            .filter { it.id in usage.favorites }
            .sortedByDescending { usage.favorites[it.id] }
        SmartList.RECENTLY_ADDED -> tracks
            .sortedWith(compareByDescending<Track> { it.dateAddedSec }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .take(limit)
        SmartList.RECENTLY_PLAYED -> tracks
            .filter { it.id in usage.plays }
            .sortedByDescending { usage.plays.getValue(it.id).lastPlayedMs }
            .take(limit)
        SmartList.MOST_PLAYED -> tracks
            .filter { (usage.plays[it.id]?.playCount ?: 0) > 0 }
            .sortedWith(
                compareByDescending<Track> { usage.plays.getValue(it.id).playCount }
                    .thenByDescending { usage.plays.getValue(it.id).lastPlayedMs },
            )
            .take(limit)
    }

    /**
     * How long a song must play before it counts as played: half of it, but never more than 30 seconds,
     * so skipping through a list doesn't inflate the counts.
     */
    fun playThresholdMs(durationMs: Long): Long =
        if (durationMs <= 0) PLAY_THRESHOLD_MS else minOf(PLAY_THRESHOLD_MS, durationMs / 2).coerceAtLeast(1)

    private const val PLAY_THRESHOLD_MS = 30_000L
}
