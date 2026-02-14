package com.cdhgold.clickcall.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
        private const val IMAGES_FOLDER = "contact_images"
        private const val PREFS_NAME = "contact_backup"
        private const val PREFS_KEY = "contacts_json"
    }

    private val gson = Gson()
    private val contactsFile: File = File(context.filesDir, CONTACTS_FILE)
    private val listType: Type = object : TypeToken<List<Contact>>() {}.type
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _contactsFlow = MutableStateFlow<List<Contact>>(loadWithRestore())
    val allContacts: Flow<List<Contact>> = _contactsFlow

    // ── Load & Restore ──────────────────────────────────────────

    private fun loadWithRestore(): List<Contact> {
        val internal = loadFromFile()
        if (internal.isNotEmpty()) {
            Log.d(TAG, "Loaded ${internal.size} contacts from internal storage")
            return internal
        }

        // Try SharedPreferences backup (survives reinstall via Auto Backup)
        Log.d(TAG, "Internal storage empty, trying SharedPreferences...")
        val prefsRestored = restoreFromPrefs()
        if (prefsRestored.isNotEmpty()) {
            Log.d(TAG, "Restored ${prefsRestored.size} contacts from SharedPreferences")
            saveToFile(prefsRestored)
            return prefsRestored
        }

        // Then try MediaStore Downloads backup
        Log.d(TAG, "SharedPreferences empty, trying MediaStore backup...")
        val restored = restoreFromMediaStore()
        if (restored.isNotEmpty()) {
            Log.d(TAG, "Restored ${restored.size} contacts from MediaStore backup")
            saveToFile(restored)
        } else {
            Log.d(TAG, "No backup found. Use menu > Import to restore manually.")
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

    // ── Save & Backup ───────────────────────────────────────────

    private fun saveToFile(list: List<Contact>) {
        try {
            val json = gson.toJson(list)
            contactsFile.writeText(json)
            prefs.edit().putString(PREFS_KEY, json).apply()
            backupToMediaStore(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── MediaStore Backup (Downloads/ClickCall/) ────────────────

    private fun backupToMediaStore(json: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                // Delete existing backup first
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER/", BACKUP_FILE)
                resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, selectionArgs)

                // Write new backup
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILE)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        stream.write(json.toByteArray())
                    }
                    Log.d(TAG, "Backup saved via MediaStore: $it")
                }
            } else {
                val backupDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER
                )
                if (!backupDir.exists()) backupDir.mkdirs()
                File(backupDir, BACKUP_FILE).writeText(json)
                Log.d(TAG, "Backup saved to Downloads/$BACKUP_FOLDER/$BACKUP_FILE")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backup to MediaStore failed", e)
        }
    }

    private fun restoreFromMediaStore(): List<Contact> {
        Log.d(TAG, "Attempting restore from MediaStore backup...")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER/", BACKUP_FILE)

                val cursor = resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    selectionArgs,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        resolver.openInputStream(uri)?.use { stream ->
                            val json = stream.bufferedReader().readText()
                            if (json.isNotBlank()) {
                                return gson.fromJson(json, listType) ?: emptyList()
                            }
                        }
                    }
                }
                emptyList()
            } else {
                val backupFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "$BACKUP_FOLDER/$BACKUP_FILE"
                )
                if (!backupFile.exists()) return emptyList()
                val json = backupFile.readText()
                if (json.isBlank()) return emptyList()
                gson.fromJson(json, listType) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore from MediaStore failed", e)
            emptyList()
        }
    }

    // ── Image Backup (app internal storage) ─────────────────────

    private fun getImagesDir(): File {
        val dir = File(context.filesDir, IMAGES_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Copy image from content:// URI to app internal storage.
     * Returns the file path string, or null on failure.
     */
    private fun copyImageToInternal(contentUri: Uri, contactId: Int): String? {
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

    private fun deleteImage(contactId: Int) {
        try {
            val file = File(getImagesDir(), "contact_$contactId.jpg")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image for contact $contactId", e)
        }
    }

    /**
     * If imageUri is a content:// URI, copy to internal storage and return file path.
     * If already a file path or null, return as-is.
     */
    private fun processImage(imageUri: String?, contactId: Int): String? {
        if (imageUri.isNullOrBlank()) return imageUri
        if (!imageUri.startsWith("content://")) return imageUri
        return try {
            copyImageToInternal(Uri.parse(imageUri), contactId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image", e)
            imageUri
        }
    }

    // ── CRUD ────────────────────────────────────────────────────

    suspend fun addContact(contact: Contact): Boolean {
        return withContext(Dispatchers.IO) {
            val current = _contactsFlow.value.toMutableList()
            if (current.size >= MAX_CONTACTS) return@withContext false

            val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
            val savedImageUri = processImage(contact.imageUri, newId)
            val toInsert = contact.copy(id = newId, imageUri = savedImageUri)
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
                val savedImageUri = processImage(contact.imageUri, contact.id)
                current[idx] = contact.copy(imageUri = savedImageUri)
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
                deleteImage(contact.id)
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

    // ── SAF Export / Import ─────────────────────────────────────

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
