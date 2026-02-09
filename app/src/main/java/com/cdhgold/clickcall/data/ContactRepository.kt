package com.cdhgold.clickcall.data

import android.content.Context
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
        private const val MAX_CONTACTS = 10
        private const val CONTACTS_FILE = "contacts.json"
    }

    private val gson = Gson()
    private val contactsFile: File = File(context.filesDir, CONTACTS_FILE)
    private val listType: Type = object : TypeToken<List<Contact>>() {}.type

    private val _contactsFlow = MutableStateFlow<List<Contact>>(loadFromFile())
    val allContacts: Flow<List<Contact>> = _contactsFlow

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addContact(contact: Contact): Boolean {
        return withContext(Dispatchers.IO) {
            val current = _contactsFlow.value.toMutableList()
            if (current.size >= MAX_CONTACTS) return@withContext false

            val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
            val toInsert = contact.copy(id = newId)
            current.add(0, toInsert) // newest first
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
}
