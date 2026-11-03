package com.euphony.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.euphony.player.model.LayoutMode
import com.euphony.player.model.SmartList
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.TextSecondary

/** Shows a short confirmation ("Added to queue") at the bottom of the screen. */
val LocalMessenger = staticCompositionLocalOf<(String) -> Unit> { {} }

fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun pluralTracks(count: Int) = if (count == 1) "1 track" else "$count tracks"

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

fun smartListIcon(list: SmartList): ImageVector = when (list) {
    SmartList.FAVORITES -> Icons.Rounded.Favorite
    SmartList.RECENTLY_ADDED -> Icons.Rounded.NewReleases
    SmartList.RECENTLY_PLAYED -> Icons.Rounded.History
    SmartList.MOST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingUp
}

/** A sort icon that opens the list of [options], with a tick on [selected]. */
@Composable
fun <T> SortButton(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort by ${label(selected)}", tint = TextSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    trailingIcon = { if (option == selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Accent) },
                )
            }
        }
    }
}

/** Switches between cards in a grid and a compact list. */
@Composable
fun LayoutToggle(layout: LayoutMode, onChange: (LayoutMode) -> Unit) {
    IconButton(onClick = { onChange(if (layout == LayoutMode.GRID) LayoutMode.LIST else LayoutMode.GRID) }) {
        if (layout == LayoutMode.GRID) {
            Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = "Show as list", tint = TextSecondary)
        } else {
            Icon(Icons.Rounded.GridView, contentDescription = "Show as grid", tint = TextSecondary)
        }
    }
}
