package com.euphony.player.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.SurfaceHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cover art embedded in the audio files, decoded at the size needed and cached in memory. */
object CoverArtLoader {
    const val SMALL_PX = 160
    const val LARGE_PX = 900

    // Up to ~24 MB of decoded bitmaps; a small thumbnail is ~100 KB, a large one ~3 MB.
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // Uris known to have no art, so lists don't retry them on every scroll.
    private val missing = object : LruCache<String, Boolean>(512) {}

    // Keeps disk reads from piling up when a long list is flung.
    private val io = Dispatchers.IO.limitedParallelism(3)

    private fun key(uri: String, px: Int) = "$px|$uri"

    fun cached(uri: String, px: Int): Bitmap? = cache.get(key(uri, px))

    suspend fun load(resolver: ContentResolver, uri: String, px: Int): Bitmap? {
        val key = key(uri, px)
        cache.get(key)?.let { return it }
        if (missing.get(key) != null) return null
        val bitmap = withContext(io) {
            val parsed = Uri.parse(uri)
            runCatching {
                // MediaStore has ready-made thumbnails; files from the music folder carry their own picture.
                if (parsed.authority == MediaStore.AUTHORITY) resolver.loadThumbnail(parsed, Size(px, px), null) else embeddedPicture(resolver, parsed, px)
            }.getOrNull()
        }
        if (bitmap != null) cache.put(key, bitmap) else missing.put(key, true)
        return bitmap
    }

    private fun embeddedPicture(resolver: ContentResolver, uri: Uri, px: Int): Bitmap? {
        val bytes = resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(pfd.fileDescriptor)
                retriever.embeddedPicture
            } finally {
                retriever.release()
            }
        } ?: return null
        // Decode at roughly the requested size rather than the full (often 3000 px) artwork.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= px * 3 / 2) return decoded
        val scale = px.toFloat() / longest
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            .also { if (it !== decoded) decoded.recycle() }
    }
}

@Composable
fun CoverArt(
    uri: String?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    corner: Dp = 8.dp,
) {
    val px = if (large) CoverArtLoader.LARGE_PX else CoverArtLoader.SMALL_PX
    val resolver = LocalContext.current.contentResolver
    val state = remember(uri, px) { mutableStateOf(uri?.let { CoverArtLoader.cached(it, px) }) }
    LaunchedEffect(uri, px) {
        if (uri != null && state.value == null) state.value = CoverArtLoader.load(resolver, uri, px)
    }
    val bitmap = state.value
    val shape = RoundedCornerShape(corner)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(SurfaceHigh, Accent.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Accent.copy(alpha = 0.75f),
                modifier = Modifier.size(minOf(maxWidth, maxHeight) * 0.42f),
            )
        }
    }
}
