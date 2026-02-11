package com.cdhgold.clickcall.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

class ContactRepository(private val context: Context) {

    companion object {
        private const val MAX_CONTACTS = 30
        private const val MAX_PRIORITY = 3
        private const val CONTACTS_FILE = "contacts.json"
        private const val BACKUP_FOLDER = "ClickCall"
        private const val BACKUP_FILE = "contacts_backup.json"
    }

    private val gson = Gson()
    private val contactsFile: File = File(context.filesDir, CONTACTS_FILE)
    private val listType: Type = object : TypeToken<List<Contact>>() {}.type

    private val _contactsFlow = MutableStateFlow<List<Contact>>(loadWithRestore())
    val allContacts: Flow<List<Contact>> = _contactsFlow

    /**
     * Load from internal file. If empty, try restoring from Downloads backup.
     */
    private fun loadWithRestore(): List<Contact> {
        val internal = loadFromFile()
        if (internal.isNotEmpty()) return internal

        // Try restore from backup
        val restored = restoreFromBackup()
        if (restored.isNotEmpty()) {
            saveToFile(restored)
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
            backupToDownloads(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Backup contacts JSON to Downloads/ClickCall/contacts_backup.json
     */
    private fun backupToDownloads(json: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: MediaStore API
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
                }
            } else {
                // Android 9 and below: direct file access
                val backupDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER
                )
                if (!backupDir.exists()) backupDir.mkdirs()
                File(backupDir, BACKUP_FILE).writeText(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Restore contacts from Downloads/ClickCall/contacts_backup.json
     */
    private fun restoreFromBackup(): List<Contact> {
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
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addContact(contact: Contact): Boolean {
        return withContext(Dispatchers.IO) {
            val current = _contactsFlow.value.toMutableList()
            if (current.size >= MAX_CONTACTS) return@withContext false

            val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
            val toInsert = contact.copy(id = newId)
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
                current[idx] = contact
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
}
