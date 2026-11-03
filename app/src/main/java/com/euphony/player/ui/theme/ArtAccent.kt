package com.euphony.player.ui.theme

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Picks an accent colour from album art: the most prominent vivid hue, made light enough (dark
 * themes) or dark enough (light theme) to read as text. Pure ARGB maths, so it is unit tested.
 */
object ArtAccent {

    private const val HUE_BUCKETS = 24

    /** [pixels] are ARGB ints, e.g. a small downscaled copy of the cover. Null for grey or empty art. */
    fun pick(pixels: IntArray, light: Boolean): Int? {
        val weight = FloatArray(HUE_BUCKETS)
        val sumR = FloatArray(HUE_BUCKETS)
        val sumG = FloatArray(HUE_BUCKETS)
        val sumB = FloatArray(HUE_BUCKETS)
        var counted = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val (h, s, v) = hsv(r, g, b)
            counted++
            // Near-greys, near-blacks and washed-out whites say little about the cover's colour.
            if (s < 0.25f || v < 0.2f) continue
            val bucket = ((h / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            val w = s * v
            weight[bucket] += w
            sumR[bucket] += r * w
            sumG[bucket] += g * w
            sumB[bucket] += b * w
        }
        if (counted == 0) return null
        val best = weight.indices.maxBy { weight[it] }
        // Ignore a hue that only shows up in a few specks.
        if (weight[best] < counted * 0.03f) return null
        val r = sumR[best] / weight[best]
        val g = sumG[best] / weight[best]
        val b = sumB[best] / weight[best]
        val (h, s, l) = hsl(r, g, b)
        val sat = s.coerceIn(0.45f, 0.85f)
        // Lighten (dark themes) or darken (light theme) until text in this colour has 4.5:1 contrast.
        var lightness = if (light) l.coerceIn(0.25f, 0.45f) else l.coerceIn(0.55f, 0.80f)
        val step = if (light) -0.02f else 0.02f
        while (lightness in 0.1f..0.92f && !readable(hslToArgb(h, sat, lightness), light)) lightness += step
        return hslToArgb(h, sat, lightness)
    }

    /** WCAG contrast of at least 4.5:1 against the app's dark (#0E0F13) or light (#F6F7F9) background. */
    private fun readable(argb: Int, light: Boolean): Boolean {
        val y = luminance(argb)
        return if (light) (BG_LIGHT + 0.05f) / (y + 0.05f) >= 4.5f else (y + 0.05f) / (BG_DARK + 0.05f) >= 4.5f
    }

    /** Relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Float {
        fun lin(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(argb shr 16 and 0xFF) + 0.7152f * lin(argb shr 8 and 0xFF) + 0.0722f * lin(argb and 0xFF)
    }

    private const val BG_DARK = 0.0049f
    private const val BG_LIGHT = 0.926f

    private fun hsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        return Triple(hue(r, g, b, max, d), if (max == 0f) 0f else d / max, max)
    }

    private fun hsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val l = (max + min) / 2
        val s = if (d == 0f) 0f else d / (1 - abs(2 * l - 1))
        return Triple(hue(r, g, b, max, d), s, l)
    }

    private fun hue(r: Float, g: Float, b: Float, max: Float, d: Float): Float {
        if (d == 0f) return 0f
        val h = when (max) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * (((b - r) / d) + 2)
            else -> 60 * (((r - g) / d) + 4)
        }
        return if (h < 0) h + 360 else h
    }

    fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
