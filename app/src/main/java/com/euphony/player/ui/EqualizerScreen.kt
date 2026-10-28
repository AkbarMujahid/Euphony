package com.euphony.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.EQ_BAND_FREQUENCIES_HZ
import com.euphony.player.model.EQ_MAX_DB
import com.euphony.player.model.EQ_MIN_DB
import com.euphony.player.model.EqualizerState
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.Surface
import com.euphony.player.ui.theme.SurfaceHigh
import com.euphony.player.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(vm: UiViewModel, state: EqualizerState, contentPadding: PaddingValues) {
    val eq = AppGraph.equalizer
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = state.enabled,
                onCheckedChange = { eq.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                modifier = Modifier.padding(end = 16.dp).semantics { contentDescription = "Equalizer on" },
            )
        }

        if (!state.supported) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceHigh)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    "This device doesn't expose an audio equalizer. Your settings are saved but won't change the sound.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val dim = if (state.enabled) 1f else 0.45f
        Text("Preset", style = MaterialTheme.typography.titleSmall, color = TextSecondary, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            eq.presets.forEach { preset ->
                FilterChip(
                    selected = state.presetName == preset.name,
                    onClick = { eq.applyPreset(preset.name) },
                    label = { Text(preset.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.2f),
                        selectedLabelColor = Accent,
                    ),
                )
            }
            if (eq.presets.none { it.name == state.presetName }) {
                FilterChip(selected = true, onClick = {}, label = { Text(state.presetName) })
            }
        }

        Column(Modifier.alpha(dim)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Preamp", style = MaterialTheme.typography.titleSmall, color = TextSecondary, modifier = Modifier.weight(1f))
                Text(formatDb(state.preampDb), style = MaterialTheme.typography.labelLarge, color = Accent)
            }
            Slider(
                value = state.preampDb,
                onValueChange = { eq.setPreamp(it) },
                valueRange = EQ_MIN_DB..EQ_MAX_DB,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = SurfaceHigh),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Text("Bands", style = MaterialTheme.typography.titleSmall, color = TextSecondary, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            ) {
                EQ_BAND_FREQUENCIES_HZ.forEachIndexed { index, hz ->
                    val gain = state.bandGainsDb.getOrElse(index) { 0f }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            gain.roundToInt().let { if (it > 0) "+$it" else "$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (gain != 0f) Accent else TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        VerticalGainSlider(
                            value = gain,
                            onValueChange = { eq.setBandGain(index, it) },
                            label = formatHz(hz),
                            modifier = Modifier.height(220.dp).width(32.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(formatHz(hz), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                    }
                }
            }
            Text(
                "Gains range from ${EQ_MIN_DB.roundToInt()} to +${EQ_MAX_DB.roundToInt()} dB. Lower the preamp if boosted bands clip.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val THUMB_RADIUS = 9.dp

private fun formatHz(hz: Int) = if (hz >= 1000) "${hz / 1000}k" else hz.toString()

private fun formatDb(db: Float) = "%+.1f dB".format(db)

/** A vertical dB slider: drag or tap along the track; snaps to 0.5 dB steps. */
@Composable
private fun VerticalGainSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier = Modifier) {
    val range = EQ_MAX_DB - EQ_MIN_DB
    val onChange by rememberUpdatedState(onValueChange)
    // Same geometry as the drawing below: the track is inset by the thumb radius at both ends.
    fun valueAt(y: Float, height: Float, inset: Float): Float {
        val fraction = 1f - ((y - inset) / (height - 2 * inset)).coerceIn(0f, 1f)
        return (((EQ_MIN_DB + fraction * range) * 2).roundToInt() / 2f).coerceIn(EQ_MIN_DB, EQ_MAX_DB)
    }
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { onChange(valueAt(it.y, size.height.toFloat(), THUMB_RADIUS.toPx())) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onChange(valueAt(change.position.y, size.height.toFloat(), THUMB_RADIUS.toPx()))
                }
            }
            .semantics {
                contentDescription = "$label Hz"
                stateDescription = formatDb(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value, EQ_MIN_DB..EQ_MAX_DB, steps = (range * 2).toInt() - 1)
                setProgress { target ->
                    onValueChange(target.coerceIn(EQ_MIN_DB, EQ_MAX_DB))
                    true
                }
            },
    ) {
        val trackWidth = 4.dp.toPx()
        val cx = size.width / 2
        val thumbRadius = THUMB_RADIUS.toPx()
        val top = thumbRadius
        val usable = size.height - thumbRadius * 2
        fun yFor(db: Float) = top + (1f - (db - EQ_MIN_DB) / range) * usable
        val zeroY = yFor(0f)
        val valueY = yFor(value)

        drawRoundRect(
            color = SurfaceHigh,
            topLeft = Offset(cx - trackWidth / 2, top),
            size = Size(trackWidth, usable),
            cornerRadius = CornerRadius(trackWidth / 2),
        )
        drawLine(TextSecondary.copy(alpha = 0.4f), Offset(cx - 8.dp.toPx(), zeroY), Offset(cx + 8.dp.toPx(), zeroY), strokeWidth = 1.dp.toPx())
        drawRoundRect(
            color = Accent,
            topLeft = Offset(cx - trackWidth / 2, minOf(zeroY, valueY)),
            size = Size(trackWidth, kotlin.math.abs(zeroY - valueY)),
            cornerRadius = CornerRadius(trackWidth / 2),
        )
        drawCircle(Accent, radius = thumbRadius, center = Offset(cx, valueY))
        drawCircle(Surface, radius = thumbRadius * 0.4f, center = Offset(cx, valueY))
    }
}
