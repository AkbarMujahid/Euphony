package com.euphony.player.playback

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.core.content.edit
import java.io.File

/**
 * The last queue and position, so playback picks up where it left off after the app or phone restarts.
 * The queue is rewritten only when it changes; the position is small and saved often.
 */
class ResumeStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "resume_queue.txt"))
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    /** Call off the main thread. */
    @Synchronized
    fun saveQueue(items: List<QueueItem>) {
        val out = file.startWrite()
        try {
            out.write(ResumeCodec.encode(items).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "Could not save the queue", e)
        }
    }

    fun savePosition(p: ResumePosition) = prefs.edit {
        putInt(KEY_INDEX, p.index)
        putString(KEY_MEDIA_ID, p.mediaId)
        putLong(KEY_POSITION, p.positionMs)
        putBoolean(KEY_SHUFFLE, p.shuffle)
        putInt(KEY_REPEAT, p.repeatMode)
    }

    /** Call off the main thread. */
    @Synchronized
    fun loadQueue(): List<QueueItem> = runCatching {
        if (!file.baseFile.exists()) emptyList() else ResumeCodec.decode(String(file.readFully(), Charsets.UTF_8))
    }.getOrElse {
        Log.w(TAG, "Ignoring unreadable saved queue", it)
        emptyList()
    }

    fun loadPosition(): ResumePosition? {
        val mediaId = prefs.getString(KEY_MEDIA_ID, null) ?: return null
        return ResumePosition(
            index = prefs.getInt(KEY_INDEX, 0),
            mediaId = mediaId,
            positionMs = prefs.getLong(KEY_POSITION, 0),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
        )
    }

    private companion object {
        const val TAG = "ResumeStore"
        const val KEY_INDEX = "index"
        const val KEY_MEDIA_ID = "mediaId"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
    }
}
