package com.euphony.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeCodecTest {

    private fun item(id: String, title: String = "Song $id") = QueueItem(id, "content://tree/doc/$id", title, "Artist", "Album")

    private fun position(index: Int, id: String, ms: Long = 42_000) = ResumePosition(index, id, ms, shuffle = false, repeatMode = 0)

    @Test
    fun roundTripsTabsNewlinesAndBackslashes() {
        val items = listOf(item("1", "Tab\there"), item("2", "Two\nlines\\and a backslash"), QueueItem("3", "content://x", "", "", ""))
        assertEquals(items, ResumeCodec.decode(ResumeCodec.encode(items)))
    }

    @Test
    fun ignoresTextWithoutTheHeaderAndBrokenLines() {
        assertEquals(emptyList<QueueItem>(), ResumeCodec.decode("garbage"))
        val text = ResumeCodec.encode(listOf(item("1"))) + "only\ttwo\n\n"
        assertEquals(listOf(item("1")), ResumeCodec.decode(text))
    }

    @Test
    fun resumesAtTheSavedSongAndPosition() {
        val items = listOf(item("a"), item("b"), item("c"))
        val saved = position(1, "b")
        assertEquals(1, ResumeCodec.startIndex(items, saved))
        assertEquals(42_000L, ResumeCodec.startPosition(items, saved, 1))
    }

    @Test
    fun findsTheSongWhenItsIndexMoved() {
        val items = listOf(item("a"), item("c"), item("b"))
        val saved = position(1, "b")
        assertEquals(2, ResumeCodec.startIndex(items, saved))
        assertEquals(42_000L, ResumeCodec.startPosition(items, saved, 2))
    }

    @Test
    fun startsTheSongOverWhenItIsGone() {
        val items = listOf(item("a"), item("c"))
        val saved = position(5, "b")
        val index = ResumeCodec.startIndex(items, saved)
        assertEquals(1, index)
        assertEquals(0L, ResumeCodec.startPosition(items, saved, index))
    }

    @Test
    fun nothingToResume() {
        assertEquals(-1, ResumeCodec.startIndex(emptyList(), position(0, "a")))
        assertEquals(0, ResumeCodec.startIndex(listOf(item("a")), null))
    }
}
