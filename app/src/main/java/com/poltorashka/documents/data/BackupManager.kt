package com.poltorashka.documents.utils

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.poltorashka.documents.FileSecurity
import com.poltorashka.documents.data.AppDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {
    private val currentHeader = "DOCSBK02".toByteArray(Charsets.US_ASCII)
    private const val legacyIterations = 10_000
    private const val currentIterations = 150_000
    private const val keyLength = 256
    private const val saltLength = 16
    private const val gcmNonceLength = 12
    private const val legacyIvLength = 16
    private const val maxArchiveBytes = 4L * 1024 * 1024 * 1024
    private const val maxEntries = 10_000
    private val installLock = Any()
    @Volatile private var activeStaging: File? = null

    fun recoverInterruptedRestores(context: Context) = synchronized(installLock) {
        val dataDir = File(context.applicationInfo.dataDir)
        dataDir.listFiles { file -> file.isDirectory && file.name.startsWith("restore_previous_") }
            ?.forEach { rollback ->
                val committedMarker = File(dataDir, "${rollback.name}.committed")
                if (!committedMarker.exists()) {
                    val manifest = File(rollback, "original_directories")
                    if (manifest.isFile) {
                        val originalNames = manifest.readLines().toSet()
                        listOf("databases", "shared_prefs", "files").forEach { name ->
                            val old = File(rollback, name)
                            val live = File(dataDir, name)
                            if (old.exists()) {
                                if (live.exists()) check(live.deleteRecursively())
                                check(old.renameTo(live))
                            } else if (name !in originalNames && live.exists()) {
                                check(live.deleteRecursively())
                            }
                        }
                    }
                }
                if (rollback.deleteRecursively()) committedMarker.delete()
            }
        context.cacheDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("restore_") && file != activeStaging
        }?.forEach { it.deleteRecursively() }
    }

    suspend fun createBackup(context: Context, destinationUri: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val snapshot = File(context.cacheDir, "backup_${UUID.randomUUID()}.db")
            try {
                val masterKey = masterKey(context)
                val databasePassword = databasePreferences(context, masterKey)
                    .getString("db_password", null)
                    ?.takeIf { it.isNotBlank() } ?: return@withContext false

                // Export to another encrypted SQLCipher database without closing Room.
                val escapedPath = snapshot.absolutePath.replace("'", "''")
                val liveDatabase = AppDatabase.getDatabase(context)
                val source = liveDatabase.openHelper.writableDatabase
                val version = source.query("PRAGMA user_version").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
                val escapedPassword = databasePassword.replace("'", "''")
                source.execSQL("ATTACH DATABASE '$escapedPath' AS backup KEY '$escapedPassword'")
                try {
                    source.beginTransaction()
                    try {
                        source.query("SELECT sqlcipher_export('backup')").use { cursor ->
                            check(cursor.moveToFirst())
                        }
                        source.execSQL("PRAGMA backup.user_version = $version")
                        source.setTransactionSuccessful()
                    } finally {
                        source.endTransaction()
                    }
                } finally {
                    source.execSQL("DETACH DATABASE backup")
                }
                if (!snapshot.isFile || snapshot.length() == 0L) return@withContext false
                val attachmentNames = validateDatabase(snapshot, databasePassword)

                val salt = ByteArray(saltLength).also(SecureRandom()::nextBytes)
                val nonce = ByteArray(gcmNonceLength).also(SecureRandom()::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, generateKey(password, salt, currentIterations), GCMParameterSpec(128, nonce))
                    updateAAD(currentHeader)
                }
                val output = context.contentResolver.openOutputStream(destinationUri)
                    ?: return@withContext false
                output.use { destination ->
                    destination.write(currentHeader)
                    destination.write(salt)
                    destination.write(nonce)
                    CipherOutputStream(destination, cipher).use { encrypted ->
                        ZipOutputStream(encrypted).use { archive ->
                            zipFile(snapshot, "databases/documents_db", archive)
                            zipEncryptedFiles(context, attachmentNames, archive)
                            val preferences = File(context.applicationInfo.dataDir, "shared_prefs")
                            preferences.listFiles()?.filter(::isPortablePreference)?.forEach { file ->
                                zipFile(file, "shared_prefs/${file.name}", archive)
                            }
                            archive.putNextEntry(ZipEntry("keys/db_password.txt"))
                            archive.write(databasePassword.toByteArray(Charsets.UTF_8))
                            archive.closeEntry()
                        }
                    }
                }
                true
            } catch (error: Exception) {
                error.printStackTrace()
                false
            } finally {
                snapshot.delete()
            }
        }

    suspend fun restoreBackup(context: Context, sourceUri: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val staging = File(context.cacheDir, "restore_${UUID.randomUUID()}")
            try {
                activeStaging = staging
                check(staging.mkdirs())
                val zipFile = File(staging, "archive.zip")
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext false
                input.use { decryptToFile(it, password, zipFile) }

                val unpacked = File(staging, "unpacked").apply { mkdirs() }
                extractArchive(zipFile, unpacked)
                val dbFolder = File(unpacked, "databases")
                val restoredDatabase = File(dbFolder, "documents_db")
                val restoredPassword = File(unpacked, "keys/db_password.txt")
                    .takeIf { it.isFile && it.length() in 1..256 }
                    ?.readText(Charsets.UTF_8)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withContext false
                if (!restoredDatabase.isFile || restoredDatabase.length() == 0L) return@withContext false
                validateDatabase(
                    restoredDatabase, restoredPassword,
                    File(unpacked, "files"), context.filesDir
                )

                val preparedFiles = File(staging, "encrypted_files").apply { mkdirs() }
                val masterKey = masterKey(context)
                File(unpacked, "files").listFiles()?.forEach { plaintext ->
                    val target = File(preparedFiles, plaintext.name)
                    FileSecurity.getEncryptedFile(context, target).openFileOutput().use { encrypted ->
                        plaintext.inputStream().use { it.copyTo(encrypted) }
                    }
                }
                val restoredPrefs = File(unpacked, "shared_prefs").apply { mkdirs() }
                // Files were re-encrypted with this device's keyset. Keep its keysets when
                // replacing portable preferences from the archive.
                File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
                    ?.filter { it.isFile && !isPortablePreferenceName(it.name) }
                    ?.forEach { it.copyTo(File(restoredPrefs, it.name), overwrite = true) }
                installPreparedBackup(context, dbFolder, restoredPrefs, preparedFiles, restoredPassword, masterKey)
                true
            } catch (error: Exception) {
                error.printStackTrace()
                false
            } finally {
                staging.deleteRecursively()
                if (activeStaging == staging) activeStaging = null
            }
        }

    private fun decryptToFile(source: InputStream, password: String, destination: File) {
        val input = DataInputStream(source)
        val firstBytes = ByteArray(currentHeader.size).also(input::readFully)
        val cipher = if (firstBytes.contentEquals(currentHeader)) {
            val salt = ByteArray(saltLength).also(input::readFully)
            val nonce = ByteArray(gcmNonceLength).also(input::readFully)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, generateKey(password, salt, currentIterations), GCMParameterSpec(128, nonce))
                updateAAD(currentHeader)
            }
        } else {
            // Earlier versions used salt + IV + AES-CBC without a format header.
            val salt = firstBytes + ByteArray(saltLength - firstBytes.size).also(input::readFully)
            val iv = ByteArray(legacyIvLength).also(input::readFully)
            Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, generateKey(password, salt, legacyIterations), IvParameterSpec(iv))
            }
        }
        var bytesWritten = 0L
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val plaintext = cipher.update(buffer, 0, count) ?: continue
                bytesWritten += plaintext.size
                require(bytesWritten <= maxArchiveBytes)
                output.write(plaintext)
            }
            val finalBytes = cipher.doFinal() // Verifies GCM authentication or legacy CBC padding.
            bytesWritten += finalBytes.size
            require(bytesWritten <= maxArchiveBytes)
            output.write(finalBytes)
        }
    }

    private fun extractArchive(archive: File, destination: File) {
        var entryCount = 0
        var totalBytes = 0L
        val seen = mutableSetOf<String>()
        ZipInputStream(FileInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(name !in seen && isAllowedEntry(name, entry.isDirectory))
                seen.add(name)
                entryCount++
                require(entryCount <= maxEntries)
                if (!entry.isDirectory) {
                    val target = File(destination, name)
                    require(target.canonicalPath.startsWith(destination.canonicalPath + File.separator))
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= maxArchiveBytes)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun isAllowedEntry(name: String, isDirectory: Boolean): Boolean {
        if (name.startsWith('/') || '\\' in name || name.split('/').any { it == ".." }) return false
        if (isDirectory) return name in setOf("databases/", "files/", "shared_prefs/", "keys/")
        val parts = name.split('/')
        if (parts.size != 2 || parts[1].isBlank()) return false
        return when (parts[0]) {
            "databases" -> parts[1] in setOf("documents_db", "documents_db-wal", "documents_db-shm", "documents_db-journal")
            "files" -> parts[1] != "." && parts[1] != ".."
            "shared_prefs" -> parts[1].endsWith(".xml") && isPortablePreferenceName(parts[1])
            "keys" -> parts[1] == "db_password.txt"
            else -> false
        }
    }

    private fun validateDatabase(
        file: File,
        password: String,
        archivedFiles: File? = null,
        liveFiles: File? = null
    ): List<String> {
        System.loadLibrary("sqlcipher")
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath, password.toByteArray(Charsets.UTF_8), null,
            SQLiteDatabase.OPEN_READWRITE, null
        )
        try {
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok")
            }
            db.rawQuery("PRAGMA cipher_integrity_check", null).use { cursor ->
                require(!cursor.moveToFirst())
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('documents', 'folders')", null).use { cursor ->
                require(cursor.count == 2)
            }
            val gson = Gson()
            val listType = object : TypeToken<List<String>>() {}.type
            val attachmentNames = linkedSetOf<String>()
            val updates = mutableListOf<Pair<Int, String>>()
            db.rawQuery("SELECT id, photoUris FROM documents", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val paths: List<String> = gson.fromJson(cursor.getString(1), listType) ?: emptyList()
                    val rebased = paths.map { oldPath ->
                        val name = File(oldPath).name
                        require(name.isNotBlank() && name != "." && name != "..")
                        attachmentNames.add(name)
                        if (archivedFiles != null && liveFiles != null) {
                            require(File(archivedFiles, name).isFile)
                            File(liveFiles, name).absolutePath
                        } else {
                            oldPath
                        }
                    }
                    if (rebased != paths) updates.add(cursor.getInt(0) to gson.toJson(rebased))
                }
            }
            if (updates.isNotEmpty()) {
                db.beginTransaction()
                try {
                    updates.forEach { (id, paths) ->
                        db.execSQL("UPDATE documents SET photoUris = ? WHERE id = ?", arrayOf<Any>(paths, id))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            return attachmentNames.toList()
        } finally {
            db.close()
        }
    }

    private fun installPreparedBackup(
        context: Context,
        database: File,
        preferences: File,
        files: File,
        password: String,
        masterKey: MasterKey
    ) = synchronized(installLock) {
        val dataDir = File(context.applicationInfo.dataDir)
        val rollback = File(dataDir, "restore_previous_${UUID.randomUUID()}")
        check(rollback.mkdirs())
        val committedMarker = File(dataDir, "${rollback.name}.committed")
        val replacements = listOf(
            database to File(dataDir, "databases"),
            preferences to File(dataDir, "shared_prefs"),
            files to context.filesDir
        )
        val movedOld = mutableListOf<Pair<File, File>>()
        val installed = mutableListOf<File>()
        var committed = false
        try {
            File(rollback, "original_directories").writeText(
                replacements.map { it.second }.filter { it.exists() }.joinToString("\n") { it.name }
            )
            AppDatabase.closeAndReset()
            replacements.forEach { (_, live) ->
                if (live.exists()) {
                    val saved = File(rollback, live.name)
                    check(live.renameTo(saved))
                    movedOld.add(saved to live)
                }
            }
            replacements.forEach { (prepared, live) ->
                check(prepared.renameTo(live))
                installed.add(live)
            }
            check(databasePreferences(context, masterKey).edit().putString("db_password", password).commit())
            check(committedMarker.createNewFile())
            committed = true
        } finally {
            if (committed) {
                if (rollback.deleteRecursively()) committedMarker.delete()
            } else {
                installed.forEach { it.deleteRecursively() }
                movedOld.asReversed().forEach { (saved, live) -> check(saved.renameTo(live)) }
                rollback.deleteRecursively()
            }
        }
    }

    private fun zipFile(file: File, name: String, zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun zipEncryptedFiles(context: Context, names: List<String>, zip: ZipOutputStream) {
        names.forEach { name ->
            require(name.isNotBlank() && name != "." && name != "..")
            val file = File(context.filesDir, name)
            require(file.isFile && file.canonicalFile.parentFile == context.filesDir.canonicalFile)
            zip.putNextEntry(ZipEntry("files/${file.name}"))
            if (FileSecurity.isLegacyPlaintext(file)) {
                file.inputStream().use { it.copyTo(zip) }
            } else {
                FileSecurity.getEncryptedFile(context, file).openFileInput().use { it.copyTo(zip) }
            }
            zip.closeEntry()
        }
    }

    private fun isPortablePreference(file: File): Boolean =
        file.isFile && isPortablePreferenceName(file.name)

    private fun isPortablePreferenceName(name: String): Boolean =
        name.endsWith(".xml") &&
            !name.contains("secure_db_prefs") &&
            !name.contains("secure_pin_prefs") &&
            !name.contains("androidx_security")

    private fun databasePreferences(context: Context, masterKey: MasterKey) =
        EncryptedSharedPreferences.create(
            context, "secure_db_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun masterKey(context: Context) = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private fun generateKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
