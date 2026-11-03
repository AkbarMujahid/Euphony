package com.euphony.player.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.euphony.player.model.TrackStats
import com.euphony.player.model.Usage
import com.euphony.player.model.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Favorites and play counts as JSON in the app's private storage. Never leaves the device. */
class UsageStore(context: Context) : UsageRepository {

    private val file = AtomicFile(File(context.filesDir, "usage.json"))
    private val _usage = MutableStateFlow(load())
    override val usage: StateFlow<Usage> = _usage.asStateFlow()

    // One writer at a time, off the main thread; a newer snapshot simply overwrites an older one.
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun toggleFavorite(trackId: Long) = change { u ->
        val favorites = if (trackId in u.favorites) u.favorites - trackId else u.favorites + (trackId to System.currentTimeMillis())
        u.copy(favorites = favorites)
    }

    override fun recordPlay(trackId: Long, atMs: Long) = change { u ->
        val old = u.plays[trackId]
        u.copy(plays = u.plays + (trackId to TrackStats((old?.playCount ?: 0) + 1, atMs)))
    }

    private fun change(transform: (Usage) -> Usage) {
        _usage.update(transform)
        val snapshot = _usage.value
        writer.launch { save(snapshot) }
    }

    private fun load(): Usage = runCatching {
        if (!file.baseFile.exists()) return Usage()
        val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
        val favorites = root.optJSONArray("favorites") ?: JSONArray()
        val plays = root.optJSONArray("plays") ?: JSONArray()
        Usage(
            favorites = (0 until favorites.length()).associate { i ->
                val o = favorites.getJSONObject(i)
                o.getLong("id") to o.optLong("at")
            },
            plays = (0 until plays.length()).associate { i ->
                val o = plays.getJSONObject(i)
                o.getLong("id") to TrackStats(o.optInt("count"), o.optLong("last"))
            },
        )
    }.getOrElse {
        Log.w(TAG, "Could not read play history", it)
        Usage()
    }

    @Synchronized
    private fun save(u: Usage) {
        val root = JSONObject()
            .put("favorites", JSONArray().apply { u.favorites.forEach { (id, at) -> put(JSONObject().put("id", id).put("at", at)) } })
            .put(
                "plays",
                JSONArray().apply {
                    u.plays.forEach { (id, s) -> put(JSONObject().put("id", id).put("count", s.playCount).put("last", s.lastPlayedMs)) }
                },
            )
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "Could not save play history", e)
        }
    }

    private companion object {
        const val TAG = "UsageStore"
    }
}
