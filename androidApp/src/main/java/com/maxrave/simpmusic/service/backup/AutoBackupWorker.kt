package com.maxrave.simpmusic.service.backup

import android.content.ContentValues
import android.content.Context
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maxrave.common.DB_NAME
import com.maxrave.common.DOWNLOAD_EXOPLAYER_FOLDER
import com.maxrave.common.EXOPLAYER_DB_NAME
import com.maxrave.common.SETTINGS_FILENAME
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AutoBackupWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {

    private val commonRepository: CommonRepository by inject()
    private val dataStoreManager: DataStoreManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Logger.i(TAG, "Starting auto backup...")

            // Check if auto backup is still enabled
            val enabled = dataStoreManager.autoBackupEnabled.first()
            if (enabled != DataStoreManager.TRUE) {
                Logger.i(TAG, "Auto backup is disabled, skipping...")
                return@withContext Result.success()
            }

            // Get backup settings
            val backupDownloaded = dataStoreManager.backupDownloaded.first() == DataStoreManager.TRUE
            val maxFiles = dataStoreManager.autoBackupMaxFiles.first()
            val customLocation = dataStoreManager.getString("backup_location").first().takeUnless { it.isNullOrBlank() }

            // Create temp backup file
            val tempBackupFile = createBackupFile(backupDownloaded)

            // Save into the configured backup folder, or Documents/SimpMusic by default
            val success = saveBackup(tempBackupFile, customLocation)

            // Delete temp file
            tempBackupFile.delete()

            if (success) {
                // Cleanup old backups
                cleanupOldBackups(maxFiles, customLocation)

                // Update last backup time
                dataStoreManager.setAutoBackupLastTime(System.currentTimeMillis())

                Logger.i(TAG, "Auto backup completed successfully")
                Result.success()
            } else {
                Logger.e(TAG, "Failed to save backup to Downloads")
                Result.retry()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Auto backup failed: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun createBackupFile(backupDownloaded: Boolean): File {
        val tempFile = File(context.cacheDir, "temp_backup.zip")

        FileOutputStream(tempFile).buffered().use { bufferedOutput ->
            ZipOutputStream(bufferedOutput).use { zipOutputStream ->
                // Backup DataStore preferences
                val dataStoreFile = File(context.filesDir, "datastore/$SETTINGS_FILENAME.preferences_pb")
                if (dataStoreFile.exists()) {
                    zipOutputStream.putNextEntry(ZipEntry("$SETTINGS_FILENAME.preferences_pb"))
                    dataStoreFile.inputStream().buffered().use { inputStream ->
                        inputStream.copyTo(zipOutputStream)
                    }
                    zipOutputStream.closeEntry()
                }

                // Checkpoint and backup database
                commonRepository.databaseDaoCheckpoint()
                val dbPath = commonRepository.getDatabasePath()
                FileInputStream(dbPath).use { inputStream ->
                    zipOutputStream.putNextEntry(ZipEntry(DB_NAME))
                    inputStream.copyTo(zipOutputStream)
                    zipOutputStream.closeEntry()
                }

                // Backup downloaded data if enabled
                if (backupDownloaded) {
                    // Backup ExoPlayer database
                    val exoPlayerDb = context.getDatabasePath(EXOPLAYER_DB_NAME)
                    if (exoPlayerDb.exists()) {
                        zipOutputStream.putNextEntry(ZipEntry(EXOPLAYER_DB_NAME))
                        exoPlayerDb.inputStream().buffered().use { inputStream ->
                            inputStream.copyTo(zipOutputStream)
                        }
                        zipOutputStream.closeEntry()
                    }

                    // Backup download folder
                    val downloadFolder = File(context.filesDir, DOWNLOAD_EXOPLAYER_FOLDER)
                    if (downloadFolder.exists() && downloadFolder.isDirectory) {
                        backupFolder(downloadFolder, DOWNLOAD_EXOPLAYER_FOLDER, zipOutputStream)
                    }
                }
            }
        }

        return tempFile
    }

    private fun backupFolder(
        folder: File,
        baseName: String,
        zipOutputStream: ZipOutputStream,
    ) {
        if (!folder.exists() || !folder.isDirectory) return

        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                val entryName = "$baseName/${file.name}"
                zipOutputStream.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { inputStream ->
                    inputStream.copyTo(zipOutputStream)
                }
                zipOutputStream.closeEntry()
            } else if (file.isDirectory) {
                backupFolder(file, "$baseName/${file.name}", zipOutputStream)
            }
        }
    }

    private fun saveBackup(
        backupFile: File,
        customLocation: String?,
    ): Boolean {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "simpmusic_backup_$timestamp.zip"

        if (customLocation != null) {
            try {
                val treeUri = customLocation.toUri()
                val target =
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        treeUri,
                        "application/zip",
                        fileName,
                    )
                if (target != null) {
                    context.contentResolver.openOutputStream(target)?.use { output ->
                        backupFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    Logger.i(TAG, "Backup saved to configured folder: $fileName")
                    return true
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Configured backup folder unavailable, falling back to default: ${e.message}")
            }
        }

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/SimpMusic")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues,
            )

            uri?.let { outputUri ->
                context.contentResolver.openOutputStream(outputUri)?.use { output ->
                    backupFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Logger.i(TAG, "Backup saved to Documents/SimpMusic/$fileName")
                true
            } ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving backup: ${e.message}")
            false
        }
    }

    private fun cleanupOldBackups(
        maxFiles: Int,
        customLocation: String?,
    ) {
        if (customLocation != null) {
            cleanupOldBackupsInTree(customLocation, maxFiles)
            return
        }
        try {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_ADDED
            )

            val selection =
                "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"

            val selectionArgs = arrayOf("Documents/SimpMusic/", "simpmusic_backup_%.zip")

            val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)

                val backupFiles = mutableListOf<Pair<Long, String>>()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    if (name.startsWith("simpmusic_backup_") && name.endsWith(".zip")) {
                        backupFiles.add(id to name)
                    }
                }

                // Delete old files if exceeding maxFiles
                if (backupFiles.size > maxFiles) {
                    val filesToDelete = backupFiles.drop(maxFiles)
                    filesToDelete.forEach { (id, name) ->
                        val deleteUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id
                        )
                        context.contentResolver.delete(deleteUri, null, null)
                        Logger.i(TAG, "Deleted old backup: $name")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error cleaning up old backups: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
    }

    private fun cleanupOldBackupsInTree(
        customLocation: String,
        maxFiles: Int,
    ) {
        try {
            val treeUri = customLocation.toUri()
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)

            val backups =
                context.contentResolver
                    .query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        ),
                        "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} LIKE ?",
                        arrayOf("simpmusic_backup_%.zip"),
                        "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} DESC",
                    )?.use { cursor ->
                        buildList<Pair<String, String>> {
                            while (cursor.moveToNext()) {
                                add(cursor.getString(0) to cursor.getString(1))
                            }
                        }
                    } ?: return

            backups.drop(maxFiles).forEach { (docId, name) ->
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.delete(docUri, null, null)
                Logger.i(TAG, "Deleted old backup: $name")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error cleaning up old backups in configured folder: ${e.message}")
        }
    }
}
