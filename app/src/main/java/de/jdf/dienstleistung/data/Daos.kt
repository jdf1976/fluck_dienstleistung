package de.jdf.dienstleistung.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun byId(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): Customer?

    @Upsert
    suspend fun upsert(customer: Customer): Long

    @Delete
    suspend fun delete(customer: Customer)

    @Query("SELECT * FROM customers")
    suspend fun dump(): List<Customer>

    @Insert
    suspend fun insertAll(items: List<Customer>)
}

private const val JOB_LIST_QUERY = """
    SELECT j.*, c.name AS customerName,
      (SELECT COALESCE(SUM(t.endMinutes - t.startMinutes - t.breakMinutes), 0)
         FROM time_entries t WHERE t.jobId = j.id) AS totalMinutes
    FROM jobs j LEFT JOIN customers c ON c.id = j.customerId
"""

@Dao
interface JobDao {
    @Query("$JOB_LIST_QUERY ORDER BY j.updatedAt DESC")
    fun list(): Flow<List<JobListItem>>

    @Query("$JOB_LIST_QUERY WHERE j.customerId = :customerId ORDER BY j.updatedAt DESC")
    fun listForCustomer(customerId: Long): Flow<List<JobListItem>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun byId(id: Long): Flow<Job?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: Long): Job?

    @Upsert
    suspend fun upsert(job: Job): Long

    @Query("UPDATE jobs SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: JobStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(job: Job)

    @Query("SELECT * FROM jobs")
    suspend fun dump(): List<Job>

    @Insert
    suspend fun insertAll(items: List<Job>)
}

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries WHERE jobId = :jobId ORDER BY date DESC, startMinutes DESC")
    fun forJob(jobId: Long): Flow<List<TimeEntry>>

    /** Netto-Minuten im Zeitraum (Epoch-Days, inklusive). */
    @Query("SELECT COALESCE(SUM(endMinutes - startMinutes - breakMinutes), 0) FROM time_entries WHERE date BETWEEN :from AND :to")
    fun minutesBetween(from: Long, to: Long): Flow<Int>

    @Upsert
    suspend fun upsert(entry: TimeEntry): Long

    @Delete
    suspend fun delete(entry: TimeEntry)

    @Query("SELECT * FROM time_entries")
    suspend fun dump(): List<TimeEntry>

    @Insert
    suspend fun insertAll(items: List<TimeEntry>)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE jobId = :jobId ORDER BY createdAt DESC")
    fun forJob(jobId: Long): Flow<List<Note>>

    @Upsert
    suspend fun upsert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes")
    suspend fun dump(): List<Note>

    @Insert
    suspend fun insertAll(items: List<Note>)
}

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE jobId = :jobId ORDER BY id")
    fun forJob(jobId: Long): Flow<List<Material>>

    @Upsert
    suspend fun upsert(material: Material): Long

    @Delete
    suspend fun delete(material: Material)

    @Query("SELECT * FROM materials")
    suspend fun dump(): List<Material>

    @Insert
    suspend fun insertAll(items: List<Material>)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE jobId = :jobId AND kind = :kind ORDER BY createdAt DESC")
    fun forJob(jobId: Long, kind: AttachmentKind): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE jobId = :jobId")
    suspend fun allForJob(jobId: Long): List<Attachment>

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Upsert
    suspend fun upsert(attachment: Attachment): Long

    @Delete
    suspend fun delete(attachment: Attachment)

    @Query("SELECT * FROM attachments")
    suspend fun dump(): List<Attachment>

    @Insert
    suspend fun insertAll(items: List<Attachment>)
}
