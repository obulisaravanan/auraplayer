package com.mymusic.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mymusic.player.model.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SongDetailsDialog(
    song: Song,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val fileSizeMb = String.format(Locale.US, "%.2f MB", song.fileSize.toDouble() / (1024 * 1024))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Song Information", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DetailRow(label = "Title", value = song.title)
                DetailRow(label = "Artist", value = song.artist)
                DetailRow(label = "Album", value = song.album)
                DetailRow(label = "Duration", value = song.durationFormatted)
                DetailRow(label = "File Size", value = fileSizeMb)
                DetailRow(label = "File Path", value = song.filePath)
                DetailRow(label = "Year", value = if (song.year > 0) song.year.toString() else "Unknown")
                DetailRow(label = "Folder", value = song.folderName.ifEmpty { "Root" })

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Listening Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                DetailRow(label = "Status", value = if (song.isPlayed) "Played" else "Unplayed")
                DetailRow(label = "Times Played", value = "${song.playCount} times")
                DetailRow(
                    label = "First Played",
                    value = song.firstPlayedTimestamp?.let { dateFormat.format(Date(it)) } ?: "Never"
                )
                DetailRow(
                    label = "Last Played",
                    value = song.lastPlayedTimestamp?.let { dateFormat.format(Date(it)) } ?: "Never"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
