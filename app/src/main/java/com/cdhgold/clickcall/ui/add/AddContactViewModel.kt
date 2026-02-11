package com.cdhgold.clickcall.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdhgold.clickcall.data.Contact
import com.cdhgold.clickcall.data.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddContactViewModel(private val repository: ContactRepository) : ViewModel() {

    private val _addResult = MutableStateFlow<AddResult?>(null)
    val addResult: StateFlow<AddResult?> = _addResult.asStateFlow()

    private val _editContact = MutableStateFlow<Contact?>(null)
    val editContact: StateFlow<Contact?> = _editContact.asStateFlow()

    fun loadContact(contactId: Int) {
        viewModelScope.launch {
            _editContact.value = repository.getContactById(contactId)
        }
    }

    fun addContact(
        nickname: String,
        phoneNumber: String,
        imageUri: String? = null,
        isPriority: Boolean = false
    ) {
        viewModelScope.launch {
            if (nickname.isBlank()) {
                _addResult.value = AddResult.Error(ErrorType.EMPTY_NICKNAME)
                return@launch
            }

            if (phoneNumber.isBlank()) {
                _addResult.value = AddResult.Error(ErrorType.EMPTY_PHONE)
                return@launch
            }

            if (repository.isNicknameDuplicate(nickname)) {
                _addResult.value = AddResult.Error(ErrorType.DUPLICATE_NICKNAME)
                return@launch
            }

            if (isPriority && repository.isPriorityFull()) {
                _addResult.value = AddResult.Error(ErrorType.MAX_PRIORITY)
                return@launch
            }

            val contact = Contact(
                nickname = nickname.trim(),
                phoneNumber = phoneNumber.trim(),
                imageUri = imageUri,
                isPriority = isPriority
            )

            val success = repository.addContact(contact)
            if (success) {
                _addResult.value = AddResult.Success
            } else {
                _addResult.value = AddResult.Error(ErrorType.MAX_CONTACTS)
            }
        }
    }

    fun updateContact(
        contactId: Int,
        nickname: String,
        phoneNumber: String,
        imageUri: String?,
        isPriority: Boolean,
        createdAt: Long
    ) {
        viewModelScope.launch {
            if (nickname.isBlank()) {
                _addResult.value = AddResult.Error(ErrorType.EMPTY_NICKNAME)
                return@launch
            }

            if (phoneNumber.isBlank()) {
                _addResult.value = AddResult.Error(ErrorType.EMPTY_PHONE)
                return@launch
            }

            if (repository.isNicknameDuplicate(nickname, excludeId = contactId)) {
                _addResult.value = AddResult.Error(ErrorType.DUPLICATE_NICKNAME)
                return@launch
            }

            if (isPriority && repository.isPriorityFull(excludeId = contactId)) {
                _addResult.value = AddResult.Error(ErrorType.MAX_PRIORITY)
                return@launch
            }

            val contact = Contact(
                id = contactId,
                nickname = nickname.trim(),
                phoneNumber = phoneNumber.trim(),
                imageUri = imageUri,
                isPriority = isPriority,
                createdAt = createdAt
            )

            repository.updateContact(contact)
            _addResult.value = AddResult.Success
        }
    }

    fun resetResult() {
        _addResult.value = null
    }

    enum class ErrorType {
        EMPTY_NICKNAME,
        EMPTY_PHONE,
        DUPLICATE_NICKNAME,
        MAX_CONTACTS,
        MAX_PRIORITY
    }

    sealed class AddResult {
        object Success : AddResult()
        data class Error(val type: ErrorType) : AddResult()
    }
}
