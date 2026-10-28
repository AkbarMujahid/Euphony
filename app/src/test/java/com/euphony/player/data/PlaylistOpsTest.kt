package com.euphony.player.data

import com.euphony.player.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistOpsTest {

    private val base = listOf(Playlist("p1", "Road trip", listOf(10L, 20L, 30L)))

    @Test
    fun createUsesPlaceholderForBlankName() {
        val list = PlaylistOps.create(emptyList(), "id", "   ")
        assertEquals("New playlist", list.single().name)
        assertTrue(list.single().trackIds.isEmpty())
    }

    @Test
    fun renameIgnoresBlankNames() {
        assertEquals("Road trip", PlaylistOps.rename(base, "p1", " ").single().name)
        assertEquals("Drive", PlaylistOps.rename(base, "p1", "Drive").single().name)
    }

    @Test
    fun addAppendsAndAllowsDuplicates() {
        assertEquals(listOf(10L, 20L, 30L, 10L, 40L), PlaylistOps.addTracks(base, "p1", listOf(10L, 40L)).single().trackIds)
    }

    @Test
    fun removeAtDropsOneEntryAndIgnoresBadIndex() {
        assertEquals(listOf(10L, 30L), PlaylistOps.removeAt(base, "p1", 1).single().trackIds)
        assertEquals(base, PlaylistOps.removeAt(base, "p1", 9))
    }

    @Test
    fun moveReordersEntries() {
        assertEquals(listOf(30L, 10L, 20L), PlaylistOps.move(base, "p1", 2, 0).single().trackIds)
        assertEquals(base, PlaylistOps.move(base, "p1", 0, 5))
    }

    @Test
    fun deleteAndUnknownIdsLeaveOthersAlone() {
        assertTrue(PlaylistOps.delete(base, "p1").isEmpty())
        assertEquals(base, PlaylistOps.addTracks(base, "missing", listOf(1L)))
    }
}
