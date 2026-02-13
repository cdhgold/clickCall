package com.cdhgold.clickcall.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

private const val TAG = "ContactRepository"

class ContactRepository(private val context: Context) {

    companion object {
        private const val MAX_CONTACTS = 30
        private const val MAX_PRIORITY = 3
        private const val CONTACTS_FILE = "contacts.json"
        private const val BACKUP_FOLDER = "ClickCall"
        private const val BACKUP_FILE = "contacts_backup.json"
        private const val IMAGES_FOLDER = "images"
        private const val PREFS_NAME = "contact_backup"
        private const val PREFS_KEY = "contacts_json"
    }

    private val gson = Gson()
    private val contactsFile: File = File(context.filesDir, CONTACTS_FILE)
    private val listType: Type = object : TypeToken<List<Contact>>() {}.type
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _contactsFlow = MutableStateFlow<List<Contact>>(loadWithRestore())
    val allContacts: Flow<List<Contact>> = _contactsFlow

    /**
     * Load from internal file. If empty, try restoring from Downloads backup.
     */
    private fun loadWithRestore(): List<Contact> {
        val internal = loadFromFile()
        if (internal.isNotEmpty()) {
            Log.d(TAG, "Loaded ${internal.size} contacts from internal storage")
            return internal
        }

        // Try SharedPreferences backup first
        Log.d(TAG, "Internal storage empty, trying SharedPreferences...")
        val prefsRestored = restoreFromPrefs()
        if (prefsRestored.isNotEmpty()) {
            Log.d(TAG, "Restored ${prefsRestored.size} contacts from SharedPreferences")
            saveToFile(prefsRestored)
            return prefsRestored
        }

        // Then try Downloads backup
        Log.d(TAG, "SharedPreferences empty, trying Downloads backup...")
        val restored = restoreFromBackup()
        if (restored.isNotEmpty()) {
            Log.d(TAG, "Restored ${restored.size} contacts from Downloads backup")
            saveToFile(restored)
        } else {
            Log.d(TAG, "No backup found or backup is empty")
        }
        return restored
    }

    private fun loadFromFile(): List<Contact> {
        return try {
            if (!contactsFile.exists()) return emptyList()
            val json = contactsFile.readText()
            if (json.isBlank()) return emptyList()
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveToFile(list: List<Contact>) {
        try {
            val json = gson.toJson(list)
            contactsFile.writeText(json)
            prefs.edit().putString(PREFS_KEY, json).apply()
            backupToExternalStorage(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreFromPrefs(): List<Contact> {
        return try {
            val json = prefs.getString(PREFS_KEY, null)
            if (json.isNullOrBlank()) return emptyList()
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Restore from SharedPreferences failed", e)
            emptyList()
        }
    }

    /**
     * Get the shared backup directory: /storage/emulated/0/ClickCall/
     */
    private fun getBackupDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), BACKUP_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getBackupFile(): File = File(getBackupDir(), BACKUP_FILE)

    private fun getImagesDir(): File {
        val dir = File(getBackupDir(), IMAGES_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Copy image from content:// URI to /storage/emulated/0/ClickCall/images/contact_{id}.jpg
     * Returns the file path string, or null on failure.
     */
    fun copyImageToBackup(contentUri: Uri, contactId: Int): String? {
        return try {
            val destFile = File(getImagesDir(), "contact_$contactId.jpg")
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Image copied to ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image for contact $contactId", e)
            null
        }
    }

    /**
     * Delete backup image for a contact.
     */
    private fun deleteBackupImage(contactId: Int) {
        try {
            val file = File(getImagesDir(), "contact_$contactId.jpg")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete backup image for contact $contactId", e)
        }
    }

    /**
     * Backup contacts JSON to /storage/emulated/0/ClickCall/contacts_backup.json
     */
    private fun backupToExternalStorage(json: String) {
        try {
            val backupFile = getBackupFile()
            backupFile.writeText(json)
            Log.d(TAG, "Backup saved to ${backupFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Backup to external storage failed", e)
        }
    }

    /**
     * Restore contacts from /storage/emulated/0/ClickCall/contacts_backup.json
     */
    private fun restoreFromBackup(): List<Contact> {
        Log.d(TAG, "Attempting restore from external storage backup...")
        return try {
            val backupFile = getBackupFile()
            if (!backupFile.exists()) {
                Log.d(TAG, "Backup file not found: ${backupFile.absolutePath}")
                return emptyList()
            }
            val json = backupFile.readText()
            if (json.isBlank()) return emptyList()
            val contacts: List<Contact> = gson.fromJson(json, listType) ?: emptyList()
            Log.d(TAG, "Found ${contacts.size} contacts in backup file")
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Restore from backup failed", e)
            emptyList()
        }
    }

    /**
     * If imageUri is a content:// URI, copy to backup folder and return the file path.
     * If it's already a file path or null, return as-is.
     */
    private fun backupImageIfNeeded(imageUri: String?, contactId: Int): String? {
        if (imageUri.isNullOrBlank()) return imageUri
        if (!imageUri.startsWith("content://")) return imageUri
        return try {
            val uri = Uri.parse(imageUri)
            copyImageToBackup(uri, contactId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup image", e)
            imageUri
        }
    }

    suspend fun addContact(contact: Contact): Boolean {
        return withContext(Dispatchers.IO) {
            val current = _contactsFlow.value.toMutableList()
            if (current.size >= MAX_CONTACTS) return@withContext false

            val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
            val backedUpImageUri = backupImageIfNeeded(contact.imageUri, newId)
            val toInsert = contact.copy(id = newId, imageUri = backedUpImageUri)
            current.add(0, toInsert)
            saveToFile(current)
            _contactsFlow.value = current
            true
        }
    }

    suspend fun updateContact(contact: Contact) {
        withContext(Dispatchers.IO) {
            val current = _contactsFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.id == contact.id }
            if (idx >= 0) {
                val backedUpImageUri = backupImageIfNeeded(contact.imageUri, contact.id)
                current[idx] = contact.copy(imageUri = backedUpImageUri)
                saveToFile(current)
                _contactsFlow.value = current
            }
        }
    }

    suspend fun deleteContact(contact: Contact) {
        withContext(Dispatchers.IO) {
            val current = _contactsFlow.value.toMutableList()
            val removed = current.removeAll { it.id == contact.id }
            if (removed) {
                deleteBackupImage(contact.id)
                saveToFile(current)
                _contactsFlow.value = current
            }
        }
    }

    suspend fun getContactById(id: Int): Contact? {
        return withContext(Dispatchers.IO) {
            _contactsFlow.value.firstOrNull { it.id == id }
        }
    }

    suspend fun getContactCount(): Int {
        return withContext(Dispatchers.IO) {
            _contactsFlow.value.size
        }
    }

    suspend fun isNicknameDuplicate(nickname: String, excludeId: Int = -1): Boolean {
        return withContext(Dispatchers.IO) {
            _contactsFlow.value.any {
                it.nickname.equals(nickname.trim(), ignoreCase = true) && it.id != excludeId
            }
        }
    }

    suspend fun isPriorityFull(excludeId: Int = -1): Boolean {
        return withContext(Dispatchers.IO) {
            _contactsFlow.value.count { it.isPriority && it.id != excludeId } >= MAX_PRIORITY
        }
    }

    suspend fun exportToUri(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(_contactsFlow.value)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray())
                }
                Log.d(TAG, "Exported ${_contactsFlow.value.size} contacts to $uri")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                false
            }
        }
    }

    suspend fun importFromUri(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (json.isNullOrBlank()) return@withContext false

                val contacts: List<Contact> = gson.fromJson(json, listType) ?: return@withContext false
                if (contacts.isEmpty()) return@withContext false

                saveToFile(contacts)
                _contactsFlow.value = contacts
                Log.d(TAG, "Imported ${contacts.size} contacts from $uri")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                false
            }
        }
    }
}
