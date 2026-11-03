package com.euphony.player.data

import android.content.Context
import androidx.core.content.edit
import com.euphony.player.model.AppSettings
import com.euphony.player.model.MAX_CROSSFADE_SEC
import com.euphony.player.model.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App settings in SharedPreferences. Unknown or missing values fall back to the defaults. */
class SettingsStore(context: Context) : SettingsRepository {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value).let { it.copy(crossfadeSec = it.crossfadeSec.coerceIn(0, MAX_CROSSFADE_SEC)) }
        if (next == _settings.value) return
        _settings.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(prefs.getString("theme", null), d.theme),
            albumArtAccent = prefs.getBoolean("albumArtAccent", d.albumArtAccent),
            replayGain = enumOr(prefs.getString("replayGain", null), d.replayGain),
            fadePlayPause = prefs.getBoolean("fadePlayPause", d.fadePlayPause),
            crossfadeSec = prefs.getInt("crossfadeSec", d.crossfadeSec).coerceIn(0, MAX_CROSSFADE_SEC),
            bitPerfect = prefs.getBoolean("bitPerfect", d.bitPerfect),
            perDeviceEq = prefs.getBoolean("perDeviceEq", d.perDeviceEq),
            songSort = enumOr(prefs.getString("songSort", null), d.songSort),
            collectionSort = enumOr(prefs.getString("collectionSort", null), d.collectionSort),
            albumSort = enumOr(prefs.getString("albumSort", null), d.albumSort),
            folderSort = enumOr(prefs.getString("folderSort", null), d.folderSort),
            albumLayout = enumOr(prefs.getString("albumLayout", null), d.albumLayout),
            folderLayout = enumOr(prefs.getString("folderLayout", null), d.folderLayout),
        )
    }

    private fun save(s: AppSettings) = prefs.edit {
        putString("theme", s.theme.name)
        putBoolean("albumArtAccent", s.albumArtAccent)
        putString("replayGain", s.replayGain.name)
        putBoolean("fadePlayPause", s.fadePlayPause)
        putInt("crossfadeSec", s.crossfadeSec)
        putBoolean("bitPerfect", s.bitPerfect)
        putBoolean("perDeviceEq", s.perDeviceEq)
        putString("songSort", s.songSort.name)
        putString("collectionSort", s.collectionSort.name)
        putString("albumSort", s.albumSort.name)
        putString("folderSort", s.folderSort.name)
        putString("albumLayout", s.albumLayout.name)
        putString("folderLayout", s.folderLayout.name)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
