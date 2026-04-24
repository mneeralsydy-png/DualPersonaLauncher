package com.dualpersona.launcher.isolation

import android.content.Context
import android.os.Environment
import com.dualpersona.launcher.utils.EnvironmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.SecureRandom

/**
 * Manages data isolation between environments.
 *
 * Creates separate storage directories for each environment:
 * - /sdcard/DualSpace/primary/  (photos, downloads, files)
 * - /sdcard/DualSpace/hidden/   (encrypted storage)
 * - /sdcard/DualSpace/emergency/ (decoy files)
 *
 * All files in the hidden space are encrypted on disk.
 */
class DataIsolationManager(private val context: Context) {

    private val secureRandom = SecureRandom()
    private val baseDir: File
        get() = File(Environment.getExternalStorageDirectory(), "DualSpace")

    /**
     * Initialize storage directories for all environments.
     */
    suspend fun initializeStorage() = withContext(Dispatchers.IO) {
        EnvironmentType.PRIMARY.let { createEnvironmentDirs(it, encrypted = false) }
        EnvironmentType.HIDDEN.let { createEnvironmentDirs(it, encrypted = true) }
        EnvironmentType.EMERGENCY.let { createEnvironmentDirs(it, encrypted = false) }

        // Create .nomedia to prevent media scanning
        File(baseDir, ".nomedia").writeText("")
    }

    /**
     * Get the storage directory for a specific environment and subfolder.
     */
    fun getStorageDir(environment: String, subfolder: String = ""): File {
        return File(getEnvironmentDir(environment), subfolder)
    }

    /**
     * Get files in an environment's storage.
     */
    fun getFiles(environment: String, subfolder: String = ""): List<File> {
        val dir = getStorageDir(environment, subfolder)
        return dir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Save a file to an environment's storage.
     * If the environment is 'hidden', the file will be encrypted.
     */
    suspend fun saveFile(
        environment: String,
        subfolder: String,
        fileName: String,
        data: ByteArray
    ): File = withContext(Dispatchers.IO) {
        val dir = getStorageDir(environment, subfolder)
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)

        if (environment == EnvironmentType.HIDDEN) {
            // Encrypt before saving
            val encrypted = encryptData(data)
            FileOutputStream(file).use { it.write(encrypted) }
        } else {
            FileOutputStream(file).use { it.write(data) }
        }

        file
    }

    /**
     * Read a file from an environment's storage.
     * If the environment is 'hidden', the file will be decrypted.
     */
    suspend fun readFile(environment: String, subfolder: String, fileName: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(getStorageDir(environment, subfolder), fileName)

        if (!file.exists()) throw FileNotFoundException("File not found: $fileName")

        val data = FileInputStream(file).use { it.readBytes() }

        if (environment == EnvironmentType.HIDDEN) {
            decryptData(data)
        } else {
            data
        }
    }

    /**
     * Delete a file from an environment's storage.
     */
    suspend fun deleteFile(environment: String, subfolder: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getStorageDir(environment, subfolder), fileName)
        if (file.exists()) {
            // Overwrite with random data before deleting (secure erase)
            if (environment == EnvironmentType.HIDDEN) {
                val randomData = ByteArray(file.length().toInt()).also { secureRandom.nextBytes(it) }
                FileOutputStream(file).use { it.write(randomData) }
            }
            file.delete()
        } else {
            false
        }
    }

    /**
     * Get total storage size for an environment.
     */
    fun getStorageSize(environment: String): Long {
        val dir = getEnvironmentDir(environment)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Wipe all data for a specific environment.
     * Used for self-destruct feature.
     */
    suspend fun wipeEnvironment(environment: String) = withContext(Dispatchers.IO) {
        val dir = getEnvironmentDir(environment)
        if (dir.exists()) {
            dir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    // Secure erase
                    val randomData = ByteArray(file.length().toInt()).also { secureRandom.nextBytes(it) }
                    FileOutputStream(file).use { it.write(randomData) }
                    file.delete()
                }
            dir.deleteRecursively()
        }
    }

    /**
     * Get the total number of files in an environment.
     */
    fun getFileCount(environment: String): Int {
        val dir = getEnvironmentDir(environment)
        return dir.walkTopDown().count { it.isFile }
    }

    // ==================== Private helpers ====================

    private fun getEnvironmentDir(environment: String): File {
        return File(baseDir, environment)
    }

    private fun createEnvironmentDirs(environment: String, encrypted: Boolean) {
        val envDir = getEnvironmentDir(environment)
        envDir.mkdirs()
        File(envDir, "Documents").mkdirs()
        File(envDir, "Pictures").mkdirs()
        File(envDir, "Downloads").mkdirs()
        File(envDir, "Music").mkdirs()
        File(envDir, "Videos").mkdirs()
        File(envDir, "Cache").mkdirs()

        if (encrypted) {
            File(envDir, ".encrypted").writeText("1")
        }
    }

    /**
     * Simple XOR-based file encryption for hidden space files.
     * For production, integrate with EncryptionManager for AES-GCM.
     */
    private fun encryptData(data: ByteArray): ByteArray {
        // In production, use AES-256-GCM from EncryptionManager
        // This is a placeholder for the encryption layer
        val key = "DualSpaceHiddenKey".toByteArray(Charsets.UTF_8)
        val encrypted = ByteArray(data.size)
        for (i in data.indices) {
            encrypted[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        // Prepend magic header
        return "DSENC".toByteArray(Charsets.UTF_8) + encrypted
    }

    private fun decryptData(data: ByteArray): ByteArray {
        // Check for magic header
        val header = "DSENC".toByteArray(Charsets.UTF_8)
        if (data.size < header.size || !data.copyOfRange(0, header.size).contentEquals(header)) {
            return data // Not encrypted, return as-is
        }

        val encrypted = data.copyOfRange(header.size, data.size)
        val key = "DualSpaceHiddenKey".toByteArray(Charsets.UTF_8)
        val decrypted = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            decrypted[i] = (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return decrypted
    }
}
