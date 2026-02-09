package com.cdhgold.clickcall.data

// Data model for a contact. Persisted to a JSON file (contacts.json).
data class Contact(
    val id: Int = 0,
    val nickname: String,
    val phoneNumber: String,
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
