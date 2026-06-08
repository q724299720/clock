package com.smartclock.data.local

import android.content.Context
import java.io.File

object DatabaseBackupUtil {

    fun backupExistingDatabase(context: Context) {
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        backupFile(dbFile)
        backupFile(File(dbFile.parentFile, "${dbFile.name}-wal"))
        backupFile(File(dbFile.parentFile, "${dbFile.name}-shm"))
    }

    private fun backupFile(source: File) {
        if (!source.exists()) return
        val target = File(source.parentFile, "${source.name}.bak")
        runCatching {
            source.copyTo(target, overwrite = true)
        }
    }
}
