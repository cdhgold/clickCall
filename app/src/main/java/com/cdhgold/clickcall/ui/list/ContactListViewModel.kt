package com.cdhgold.clickcall.ui.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdhgold.clickcall.data.Contact
import com.cdhgold.clickcall.data.ContactRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactListViewModel(private val repository: ContactRepository) : ViewModel() {

    val allContacts: StateFlow<List<Contact>> = repository.allContacts
        .map { contacts ->
            contacts.sortedWith(compareByDescending<Contact> { it.isPriority }.thenBy { it.nickname })
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun exportContacts(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.exportToUri(uri)
            onResult(success)
        }
    }

    fun importContacts(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importFromUri(uri)
            onResult(success)
        }
    }
}
