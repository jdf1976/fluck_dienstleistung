package de.jdf.dienstleistung.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jdf.dienstleistung.App
import de.jdf.dienstleistung.R
import de.jdf.dienstleistung.data.JobStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---- Farben (siehe Gestaltungsregeln) ----

val Navy = Color(0xFF1B3352)
val SignalYellow = Color(0xFFF5B400)
private val Ground = Color(0xFFF4F6F9)
private val Ink = Color(0xFF14202E)
private val Muted = Color(0xFF4F5B6B)

private val LightColors = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F2), onPrimaryContainer = Navy,
    secondary = SignalYellow, onSecondary = Navy,
    secondaryContainer = Color(0xFFFFE8B3), onSecondaryContainer = Navy,
    tertiary = SignalYellow, onTertiary = Navy,
    background = Ground, onBackground = Ink,
    surface = Ground, onSurface = Ink,
    surfaceVariant = Color(0xFFE6EAEF), onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outline = Color(0xFFC9D2DD), outlineVariant = Color(0xFFE1E6EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7CBEA), onPrimary = Color(0xFF0F1A28),
    primaryContainer = Color(0xFF24456E), onPrimaryContainer = Color.White,
    secondary = SignalYellow, onSecondary = Navy,
    secondaryContainer = Color(0xFF5C4400), onSecondaryContainer = Color(0xFFFFE8B3),
    tertiary = SignalYellow, onTertiary = Navy,
    background = Color(0xFF0F1A28), onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF0F1A28), onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF26384D), onSurfaceVariant = Color(0xFFB5C0CE),
    surfaceContainerLowest = Color(0xFF16222F), surfaceContainerLow = Color(0xFF16222F),
    surfaceContainer = Color(0xFF16222F), surfaceContainerHigh = Color(0xFF1B2838), surfaceContainerHighest = Color(0xFF1B2838),
    outline = Color(0xFF52627A), outlineVariant = Color(0xFF2C3D52),
)

private val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.3).toInt()) =
    TextStyle(fontFamily = Figtree, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

/** Größere Schrift als Android-Standard: draußen und mit Abstand gut lesbar. */
private val AppTypography = Typography(
    displaySmall = style(52, FontWeight.ExtraBold, 58),
    headlineLarge = style(34, FontWeight.ExtraBold, 40),
    headlineMedium = style(28, FontWeight.ExtraBold, 34),
    headlineSmall = style(24, FontWeight.ExtraBold),
    titleLarge = style(21, FontWeight.Bold),
    titleMedium = style(18, FontWeight.Bold),
    titleSmall = style(16, FontWeight.Bold),
    bodyLarge = style(17, FontWeight.Normal, 24),
    bodyMedium = style(16, FontWeight.Normal, 22),
    bodySmall = style(14, FontWeight.Normal, 19),
    labelLarge = style(16, FontWeight.Bold),
    labelMedium = style(14, FontWeight.SemiBold),
    labelSmall = style(13, FontWeight.SemiBold),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

@Composable
fun app(): App = LocalContext.current.applicationContext as App

// ---- Bausteine ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", Modifier.size(28.dp))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Großer Seitentitel wie im Entwurf. */
@Composable
fun PageTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        trailing()
    }
}

/** Weiße Karte mit feinem Rand. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    else Card(modifier = modifier, shape = shape, colors = colors, border = border, content = content)
}

enum class BtnStyle { Primary, Dark, Outline }

/** Großer, handschuhtauglicher Knopf (Standard 64 dp hoch). */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.Primary,
    height: Dp = 64.dp,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(if (height >= 60.dp) 20.dp else 16.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Text("  ") }
        Text(text, style = if (height >= 60.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
    }
    val m = modifier.height(height)
    when (style) {
        BtnStyle.Primary -> Button(onClick, m, enabled, shape,
            ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = Navy)) { content() }
        BtnStyle.Dark -> Button(onClick, m, enabled, shape,
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary)) { content() }
        BtnStyle.Outline -> OutlinedButton(onClick, m, enabled, shape,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) { content() }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---- Status: immer Farbe + Symbol + Text ----

data class StatusLook(val fg: Color, val bg: Color, val icon: ImageVector)

fun statusLook(s: JobStatus): StatusLook = when (s) {
    JobStatus.REQUEST -> StatusLook(Color(0xFF3E4753), Color(0xFFE6EAEF), Icons.Default.Inbox)
    JobStatus.PLANNED -> StatusLook(Color(0xFF0B4F9C), Color(0xFFDCEBFF), Icons.Default.Event)
    JobStatus.IN_PROGRESS -> StatusLook(Color(0xFF7A4A00), Color(0xFFFFE8B3), Icons.Default.Bolt)
    JobStatus.DONE -> StatusLook(Color(0xFF17602F), Color(0xFFD7F2DF), Icons.Default.Check)
    JobStatus.INVOICED -> StatusLook(Color(0xFF55348F), Color(0xFFECE3FA), Icons.Default.Euro)
}

fun statusColor(s: JobStatus): Color = statusLook(s).fg

@Composable
fun StatusBadge(status: JobStatus) {
    val look = statusLook(status)
    Surface(shape = RoundedCornerShape(50), color = look.bg, contentColor = look.fg) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(look.icon, null, Modifier.size(16.dp))
            Text(status.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Kleiner Kennzahlen-Kasten (Stunden, Anzahl …). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(SignalYellow, RoundedCornerShape(50)))
}

// ---- Formatierung ----

private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val longDateFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

fun formatEpochDay(day: Long): String = LocalDate.ofEpochDay(day).format(dateFmt)

fun formatLongDate(d: LocalDate = LocalDate.now()): String = d.format(longDateFmt)

fun formatMillis(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFmt)

fun formatClock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** 125 -> "2:05 h" */
fun formatDuration(minutes: Int): String = "%d:%02d h".format(minutes / 60, minutes % 60)

/** Sekunden -> "01:23:45" */
fun formatStopwatch(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)

/** "7:30", "730", "7" -> Minuten seit Mitternacht */
fun parseClock(s: String): Int? {
    val t = s.trim().replace('.', ':')
    val (h, m) = when {
        ':' in t -> t.substringBefore(':') to t.substringAfter(':')
        t.length in 3..4 -> t.dropLast(2) to t.takeLast(2)
        else -> t to "0"
    }
    val hh = h.toIntOrNull() ?: return null
    val mm = m.ifEmpty { "0" }.toIntOrNull() ?: return null
    if (hh !in 0..24 || mm !in 0..59 || (hh == 24 && mm > 0)) return null
    return hh * 60 + mm
}

fun formatQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else q.toString().replace('.', ',')
