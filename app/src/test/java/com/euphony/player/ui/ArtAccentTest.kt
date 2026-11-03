package com.euphony.player.ui

import com.euphony.player.ui.theme.ArtAccent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtAccentTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = c shr 16 and 0xFF
    private fun green(c: Int) = c shr 8 and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun picksTheDominantVividColour() {
        val pixels = IntArray(100) { if (it < 70) argb(200, 30, 30) else argb(30, 30, 200) }
        val accent = ArtAccent.pick(pixels, light = false)!!
        assertTrue(red(accent) > green(accent) && red(accent) > blue(accent))
    }

    @Test
    fun greyArtHasNoAccent() {
        assertNull(ArtAccent.pick(IntArray(100) { argb(120, 120, 120) }, light = false))
        assertNull(ArtAccent.pick(IntArray(0), light = false))
    }

    @Test
    fun accentIsReadableOnTheTheme() {
        // A very dark navy cover still gives a light accent on dark themes and a dark one on the light theme.
        val navy = IntArray(64) { argb(10, 20, 90) }
        val onDark = ArtAccent.pick(navy, light = false)
        val onLight = ArtAccent.pick(navy, light = true)
        assertNotNull(onDark)
        assertNotNull(onLight)
        // 4.5:1 contrast against the dark (#0E0F13) and light (#F6F7F9) backgrounds.
        assertTrue((ArtAccent.luminance(onDark!!) + 0.05f) / (0.0049f + 0.05f) >= 4.5f)
        assertTrue((0.926f + 0.05f) / (ArtAccent.luminance(onLight!!) + 0.05f) >= 4.5f)
    }
}
