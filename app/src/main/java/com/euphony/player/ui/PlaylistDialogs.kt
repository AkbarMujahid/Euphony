package com.euphony.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.Playlist
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.TextSecondary

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val valid = value.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lets the person pick an existing playlist for [trackIds], or make a new one. */
@Composable
fun AddToPlaylistDialog(trackIds: List<Long>, playlists: List<Playlist>, onDone: (String?) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                val playlist = AppGraph.playlists.create(name)
                AppGraph.playlists.addTracks(playlist.id, trackIds)
                onDone(playlist.name)
            },
            onDismiss = { onDone(null) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (trackIds.size == 1) "Add to playlist" else "Add ${pluralTracks(trackIds.size)} to playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    PickerRow(label = "New playlist…", onClick = { creating = true }, accent = true)
                }
                items(playlists, key = { it.id }) { playlist ->
                    PickerRow(
                        label = playlist.name,
                        detail = pluralTracks(playlist.trackIds.size),
                        onClick = {
                            AppGraph.playlists.addTracks(playlist.id, trackIds)
                            onDone(playlist.name)
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit, detail: String? = null, accent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (accent) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.PlaylistPlay,
            contentDescription = null,
            tint = if (accent) Accent else TextSecondary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (accent) Accent else MaterialTheme.colorScheme.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
