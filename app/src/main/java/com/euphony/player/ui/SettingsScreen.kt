package com.euphony.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.AppSettings
import com.euphony.player.model.BitPerfectStatus
import com.euphony.player.model.MAX_CROSSFADE_SEC
import com.euphony.player.model.OutputDevice
import com.euphony.player.model.ReplayGainMode
import com.euphony.player.model.ThemeMode
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.SurfaceHigh
import com.euphony.player.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: UiViewModel,
    settings: AppSettings,
    output: OutputDevice,
    bitPerfect: BitPerfectStatus,
    contentPadding: PaddingValues,
) {
    val update = AppGraph.settings::update
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        SectionTitle("Appearance")
        ThemeMode.entries.forEach { mode ->
            RadioRow(mode.label, selected = settings.theme == mode) { update { it.copy(theme = mode) } }
        }
        SwitchRow(
            title = "Colours from album art",
            body = "Tint buttons and highlights with the playing song's cover.",
            checked = settings.albumArtAccent,
        ) { checked -> update { it.copy(albumArtAccent = checked) } }

        SectionTitle("Playback")
        Text("ReplayGain", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Evens out loudness using the ReplayGain tags in your files. Track mode levels every song; " +
                "album mode keeps the loudness differences within an album. Songs without tags play unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        ChipRow(ReplayGainMode.entries.map { it.label }, ReplayGainMode.entries.indexOf(settings.replayGain)) { i ->
            update { it.copy(replayGain = ReplayGainMode.entries[i]) }
        }
        SwitchRow(
            title = "Fade on play and pause",
            body = "A short fade instead of an abrupt start or stop.",
            checked = settings.fadePlayPause,
        ) { checked -> update { it.copy(fadePlayPause = checked) } }
        CrossfadeRow(settings.crossfadeSec) { sec -> update { it.copy(crossfadeSec = sec) } }

        SectionTitle("Equalizer")
        SwitchRow(
            title = "Separate equalizer for each output",
            body = "Phone speaker, wired headphones, each Bluetooth device and each USB DAC keep their own settings, " +
                "switching automatically when they connect. Now playing through: ${output.name}.",
            checked = settings.perDeviceEq,
        ) { checked -> update { it.copy(perDeviceEq = checked) } }

        SectionTitle("USB DAC")
        SwitchRow(
            title = "Bit-perfect output",
            body = "Sends the audio to a USB DAC exactly as decoded: no resampling or mixing. Needs Android 14 or newer.",
            checked = settings.bitPerfect,
        ) { checked -> update { it.copy(bitPerfect = checked) } }
        if (settings.bitPerfect) {
            Notice(bitPerfectText(bitPerfect))
            Notice(
                "While bit-perfect output is active, the equalizer, ReplayGain, fades, crossfade and the phone's volume " +
                    "control are bypassed. Set the volume on the DAC or in its app, and start low. Other apps' sounds may be muted.",
            )
        }

        SectionTitle("About")
        Text(
            "Euphony ${appVersion()} · offline, no internet permission. Plays only the folder you picked.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

private fun bitPerfectText(status: BitPerfectStatus): String = when (status) {
    BitPerfectStatus.Off -> "Off."
    BitPerfectStatus.NeedsAndroid14 -> "This phone runs an Android version older than 14, so bit-perfect output isn't available."
    BitPerfectStatus.NoUsbDac -> "No USB DAC connected. Plug one in and start a song."
    is BitPerfectStatus.Ready -> "${status.dac} connected. Bit-perfect output starts with the next song."
    is BitPerfectStatus.NoMatch -> "${status.dac} has no bit-perfect mode for ${status.format}, so this song plays the normal way."
    is BitPerfectStatus.Active -> "Active: ${status.format} to ${status.dac}."
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Accent,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.2f), selectedLabelColor = Accent),
            )
        }
    }
}

@Composable
private fun CrossfadeRow(seconds: Int, onChange: (Int) -> Unit) {
    // The slider moves freely; the setting is saved when it's let go.
    var dragging by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragging >= 0f) dragging.roundToInt() else seconds
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crossfade", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (shown == 0) "Off (gapless)" else "$shown s", style = MaterialTheme.typography.labelLarge, color = Accent)
        }
        Text(
            "Blends the end of each song into the next. Off keeps albums gapless.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Slider(
            value = if (dragging >= 0f) dragging else seconds.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                onChange(dragging.roundToInt())
                dragging = -1f
            },
            valueRange = 0f..MAX_CROSSFADE_SEC.toFloat(),
            steps = MAX_CROSSFADE_SEC - 1,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = SurfaceHigh),
        )
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = Accent)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(2.dp))
}
