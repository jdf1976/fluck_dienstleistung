package de.jdf.dienstleistung.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import de.jdf.dienstleistung.data.Customer
import de.jdf.dienstleistung.data.Job
import de.jdf.dienstleistung.data.Material
import de.jdf.dienstleistung.data.address
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Materialliste als lesbarer Text für WhatsApp / E-Mail.
 * *fett* wird in WhatsApp fett dargestellt und stört in E-Mails nicht.
 */
fun materialListText(job: Job, customer: Customer?, mats: List<Material>): String = buildString {
    appendLine("*Materialliste*")
    appendLine("Auftrag: ${job.title}")
    customer?.let { appendLine("Kunde: ${it.name}") }
    val place = job.location.ifBlank { customer?.address.orEmpty() }
    if (place.isNotBlank()) appendLine("Ort: $place")
    appendLine("Stand: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
    appendLine()
    mats.forEach { m ->
        val note = if (m.note.isNotBlank()) " (${m.note})" else ""
        appendLine("• ${formatQty(m.quantity)} ${m.unit}  ${m.name}$note")
    }
    appendLine()
    append("${mats.size} Positionen")
}

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Öffnet die Android-Auswahl (WhatsApp, E-Mail, SMS …). */
fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Materialliste senden").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
