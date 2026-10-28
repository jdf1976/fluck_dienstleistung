package de.jdf.dienstleistung.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class JobStatus(val label: String) {
    REQUEST("Angefragt"),
    PLANNED("Geplant"),
    IN_PROGRESS("In Arbeit"),
    DONE("Erledigt"),
    INVOICED("Abgerechnet"),
}

enum class AttachmentKind { PHOTO, PROTOCOL }

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val street: String = "",
    val zipCity: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
)

val Customer.address: String get() = listOf(street, zipCity).filter { it.isNotBlank() }.joinToString(", ")

@Entity(
    tableName = "jobs",
    foreignKeys = [ForeignKey(
        entity = Customer::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("customerId")],
)
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long?,
    val title: String,
    val description: String = "",
    /** Einsatzort, falls abweichend von der Kundenadresse. */
    val location: String = "",
    val status: JobStatus = JobStatus.REQUEST,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "time_entries",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    /** LocalDate.toEpochDay() */
    val date: Long,
    /** Minuten seit Mitternacht */
    val startMinutes: Int,
    val endMinutes: Int,
    val breakMinutes: Int = 0,
    val note: String = "",
)

val TimeEntry.netMinutes: Int get() = (endMinutes - startMinutes - breakMinutes).coerceAtLeast(0)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "materials",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "Stk",
    val note: String = "",
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val kind: AttachmentKind,
    /** Dateiname im App-Ordner files/attachments */
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class JobListItem(
    @Embedded val job: Job,
    val customerName: String?,
    val totalMinutes: Int,
)
