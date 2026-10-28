package de.jdf.dienstleistung.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.BuildConfigInfo
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val a = app()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { a.backup.export(uri) }
                .onSuccess { Toast.makeText(ctx, "Sicherung erstellt ($it Dateien)", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(ctx, "Fehler: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(top = 20.dp)) { Text("Backup", style = MaterialTheme.typography.headlineLarge) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Sicherung", style = MaterialTheme.typography.titleLarge)
            Text(
                "Alle Kunden, Aufträge, Zeiten, Notizen, Material, Fotos und Protokolle werden in eine ZIP-Datei gesichert. " +
                    "Speicherort frei wählbar, z. B. Nextcloud, Google Drive oder USB-Stick.",
                style = MaterialTheme.typography.bodyMedium,
            )
            BigButton("Sicherung erstellen", {
                val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
                exportLauncher.launch("JDF-Backup_$ts.zip")
            }, Modifier.fillMaxWidth(), icon = Icons.Default.Download, enabled = !busy)
            BigButton("Sicherung wiederherstellen", { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                Modifier.fillMaxWidth(), icon = Icons.Default.Upload, style = BtnStyle.Outline, height = 56.dp, enabled = !busy)

            Text("Info", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Text("Version ${BuildConfigInfo.versionName(ctx)}", style = MaterialTheme.typography.bodyMedium)
            Text("Alle Daten liegen nur lokal auf diesem Gerät. Regelmäßig sichern!", style = MaterialTheme.typography.bodySmall)
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Sicherung wiederherstellen?") },
            text = { Text("ALLE aktuellen Daten in der App werden durch den Inhalt der Sicherung ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    busy = true
                    scope.launch {
                        runCatching { a.backup.import(uri) }
                            .onSuccess { Toast.makeText(ctx, "Wiederhergestellt", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(ctx, "Fehler: ${it.message}", Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text("Ersetzen") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Abbrechen") } },
        )
    }
}
