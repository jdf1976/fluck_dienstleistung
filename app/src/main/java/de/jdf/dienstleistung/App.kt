package de.jdf.dienstleistung

import android.app.Application
import de.jdf.dienstleistung.data.AppDatabase
import de.jdf.dienstleistung.data.Backup
import de.jdf.dienstleistung.data.FileStore

class App : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val files: FileStore by lazy { FileStore(this) }
    val backup: Backup by lazy { Backup(this, db, files) }
}
