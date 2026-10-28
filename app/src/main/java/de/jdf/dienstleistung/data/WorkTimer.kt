package de.jdf.dienstleistung.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.jdf.dienstleistung.App
import de.jdf.dienstleistung.MainActivity
import de.jdf.dienstleistung.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class RunningTimer(val jobId: Long, val jobTitle: String, val startedAt: Long)

/**
 * Start/Stopp-Zeiterfassung. Der laufende Timer liegt in den SharedPreferences,
 * übersteht also App-Neustarts. Beim Stoppen entsteht ein normaler Zeiteintrag.
 */
class WorkTimer(private val context: Context, private val db: AppDatabase) {

    private val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RunningTimer?> = _state

    private fun load(): RunningTimer? {
        val id = prefs.getLong("jobId", 0L)
        if (id == 0L) return null
        return RunningTimer(id, prefs.getString("title", "").orEmpty(), prefs.getLong("startedAt", 0L))
    }

    /** Startet für [jobId]. Läuft schon ein anderer Timer, wird der vorher gespeichert. */
    suspend fun start(jobId: Long): Int? {
        val saved = if (_state.value != null) stop() else null
        val title = db.jobs().get(jobId)?.title.orEmpty()
        val t = RunningTimer(jobId, title, System.currentTimeMillis())
        prefs.edit().putLong("jobId", jobId).putString("title", title).putLong("startedAt", t.startedAt).apply()
        _state.value = t
        db.jobs().get(jobId)?.let { if (it.status == JobStatus.REQUEST || it.status == JobStatus.PLANNED) db.jobs().setStatus(jobId, JobStatus.IN_PROGRESS) }
        showNotification(t)
        return saved
    }

    /** Stoppt und speichert. Liefert die gespeicherten Minuten (0 = unter einer Minute, verworfen). */
    suspend fun stop(): Int? {
        val t = _state.value ?: return null
        prefs.edit().clear().apply()
        _state.value = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(t.startedAt).atZone(zone)
        val end = Instant.now().atZone(zone)
        if (end.toEpochSecond() - start.toEpochSecond() < 60) return 0

        val startMin = start.hour * 60 + start.minute
        // Über Mitternacht: Eintrag endet um 24:00 am Starttag
        val endMin = if (end.toLocalDate() == start.toLocalDate()) end.hour * 60 + end.minute else 24 * 60
        val entry = TimeEntry(
            jobId = t.jobId,
            date = start.toLocalDate().toEpochDay(),
            startMinutes = startMin,
            endMinutes = endMin.coerceAtLeast(startMin + 1),
            note = "Timer",
        )
        db.times().upsert(entry)
        db.jobs().touch(t.jobId)
        return entry.netMinutes
    }

    private fun showNotification(t: RunningTimer) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Laufende Zeiterfassung", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, TimerStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(t.jobTitle.ifBlank { "Zeiterfassung" })
            .setContentText("Timer läuft")
            .setWhen(t.startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stopp & speichern", stop)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    /** Nach Neustart der App die Anzeige wiederherstellen. */
    fun restoreNotification() {
        _state.value?.let { showNotification(it) }
    }

    companion object {
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 42
    }
}

/** „Stopp“-Knopf in der Benachrichtigung. */
class TimerStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as App
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.timer.stop()
            } finally {
                pending.finish()
            }
        }
    }
}
