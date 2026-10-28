package de.jdf.dienstleistung.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Customer::class, Job::class, TimeEntry::class, Note::class, Material::class, Attachment::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun jobs(): JobDao
    abstract fun times(): TimeEntryDao
    abstract fun notes(): NoteDao
    abstract fun materials(): MaterialDao
    abstract fun attachments(): AttachmentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jdf.db").build()
    }
}
