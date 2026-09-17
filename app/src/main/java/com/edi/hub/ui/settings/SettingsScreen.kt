package com.edi.hub.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.system.exitProcess

private val stamp: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/**
 * The backup slice, and the dynamic-colour switch that the fixed scheme made a setting. Nothing
 * else lives here yet — currency and the notification time arrive with the features that need them.
 */
@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.rememberFolder(tree)
    }
    // */* rather than a database MIME type: providers disagree about what a .db file is, and
    // filtering on it hides valid backups.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.proposeRestore(uri)
    }

    viewModel.message?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Hub keeps everything in one file on this phone. A backup copies that file into a " +
                "folder you choose, so you can put it on a computer or let Drive or Syncthing pick it up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val folder = viewModel.folder
        Text(
            text = folder?.let { "Folder: ${it.lastPathSegment ?: it}" } ?: "No folder chosen yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = viewModel.lastBackupAt?.let { "Last backed up ${stamp.format(it)}." }
                ?: "Never backed up.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickFolder.launch(null) }, enabled = !viewModel.busy) {
                Text(if (folder == null) "Choose folder" else "Change folder")
            }
            Button(
                onClick = viewModel::backUpNow,
                enabled = folder != null && !viewModel.busy,
            ) {
                Text("Back up now")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Restore", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Restoring replaces everything in Hub with the contents of a backup file. " +
                "Hub checks the file first and leaves your data alone if anything is wrong with it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { pickFile.launch(arrayOf("*/*")) },
            enabled = !viewModel.busy,
        ) {
            Text("Restore from a file")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Colours from my wallpaper", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Off keeps Hub's own teal, which is what the urgency colours were picked against.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = viewModel.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
        }
    }

    viewModel.pending?.let { pending ->
        RestoreConfirmation(
            fileName = pending.fileName,
            modifiedAt = pending.modifiedAt,
            onConfirm = viewModel::confirmRestore,
            onCancel = viewModel::cancelRestore,
        )
    }

    if (viewModel.restarting) RestartDialog()
}

/**
 * The one destructive action in Hub, so the dialog says all four things: what goes, what arrives,
 * which file it arrives from, and that Hub closes itself afterwards. The confirming button says
 * what it does rather than "OK", and the safe choice sits underneath it.
 */
@Composable
private fun RestoreConfirmation(
    fileName: String,
    modifiedAt: Instant?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Replace everything in Hub?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Every item, product and shopping trip now on this phone is thrown away.")
                Text(
                    text = modifiedAt?.let { "In their place Hub loads $fileName, saved ${stamp.format(it)}." }
                        ?: "In their place Hub loads $fileName.",
                )
                Text("Hub closes itself when it is done. Open it again to see the restored data.")
            }
        },
        confirmButton = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Replace my data")
                }
                TextButton(onClick = onCancel) { Text("Keep what I have") }
            }
        },
    )
}

/** The dialog has to come first: after the process is killed there is nobody left to prompt. */
@Composable
private fun RestartDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Restored") },
        text = { Text("Hub has to close and open again before it reads the restored data.") },
        confirmButton = {
            TextButton(onClick = { exitProcess(0) }) { Text("Close Hub") }
        },
    )
}
