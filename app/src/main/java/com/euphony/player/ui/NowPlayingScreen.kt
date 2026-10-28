package com.euphony.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.PlaybackUiState
import com.euphony.player.model.RepeatMode
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.Background
import com.euphony.player.ui.theme.Surface
import com.euphony.player.ui.theme.SurfaceHigh
import com.euphony.player.ui.theme.TextPrimary
import com.euphony.player.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val SLEEP_OPTIONS = listOf(15, 30, 45, 60, 90)

@Composable
fun NowPlayingScreen(vm: UiViewModel, state: PlaybackUiState) {
    val track = state.currentTrack
    var sleepDialog by remember { mutableStateOf(false) }
    val sleepRemaining = rememberRemaining(state.sleepTimerEndsAtMs)

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Accent.copy(alpha = 0.10f), Background, Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }, modifier = Modifier.padding(start = 0.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close now playing", modifier = Modifier.size(32.dp))
            }
            Text(
                "Now playing",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CoverArt(
                track?.uri,
                Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                large = true,
                corner = 20.dp,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(track?.artist, track?.album?.takeIf { it.isNotBlank() }).joinToString(" — "),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        state.formatLabel?.let { label ->
            Spacer(Modifier.height(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Accent.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        SeekBar(state)
        Spacer(Modifier.height(8.dp))
        TransportControls(state)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BottomAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") { vm.open(Screen.Queue) }
            BottomAction(Icons.Rounded.Equalizer, "Equalizer") { vm.open(Screen.Equalizer) }
            BottomAction(
                Icons.Rounded.Bedtime,
                sleepRemaining?.let { formatDuration(it) } ?: "Sleep timer",
                active = sleepRemaining != null,
            ) { sleepDialog = true }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (sleepDialog) {
        SleepTimerDialog(active = sleepRemaining != null, onPick = { minutes ->
            AppGraph.player.setSleepTimer(minutes)
            sleepDialog = false
        }, onDismiss = { sleepDialog = false })
    }
}

/** Milliseconds until [endsAtMs], ticking once a second; null when no timer is set. */
@Composable
private fun rememberRemaining(endsAtMs: Long?): Long? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) {
        while (endsAtMs != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return endsAtMs?.let { (it - now).coerceAtLeast(0) }
}

@Composable
private fun SeekBar(state: PlaybackUiState) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val position = if (dragging) (dragValue * duration).toLong() else state.positionMs.coerceIn(0, duration)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = if (dragging) dragValue else position.toFloat() / duration,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                AppGraph.player.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            enabled = state.currentTrack != null && state.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = SurfaceHigh,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(position), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun TransportControls(state: PlaybackUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(Icons.Rounded.Shuffle, "Shuffle", state.shuffle) { AppGraph.player.setShuffle(!state.shuffle) }
        IconButton(onClick = { AppGraph.player.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
        }
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable(role = Role.Button) { AppGraph.player.togglePlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(42.dp),
            )
        }
        IconButton(onClick = { AppGraph.player.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
        }
        ToggleIcon(
            if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            when (state.repeat) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ALL -> "Repeat all"
                RepeatMode.ONE -> "Repeat one"
            },
            state.repeat != RepeatMode.OFF,
        ) { AppGraph.player.cycleRepeat() }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Icon(icon, contentDescription = description, tint = if (active) Accent else TextSecondary)
            if (active) Box(Modifier.padding(top = 30.dp).size(4.dp).clip(CircleShape).background(Accent))
        }
    }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Accent else TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) Accent else TextSecondary)
    }
}

@Composable
private fun SleepTimerDialog(active: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf<Int?>(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text("Pause playback after:", color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                SLEEP_OPTIONS.forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == minutes, role = Role.RadioButton) { choice = minutes }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice == minutes,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Accent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("$minutes minutes")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text("Start") } },
        dismissButton = {
            if (active) {
                TextButton(onClick = { onPick(null) }) { Text("Turn off") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun MiniPlayer(state: PlaybackUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.currentTrack ?: state.queue.firstOrNull() ?: return
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(track.uri, Modifier.size(44.dp), corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { AppGraph.player.togglePlayPause() }) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = TextPrimary,
                )
            }
            IconButton(onClick = { AppGraph.player.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Accent,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
            drawStopIndicator = {},
        )
    }
}
