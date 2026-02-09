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

    fun addContact(
        nickname: String,
        phoneNumber: String,
        imageUri: String? = null
    ) {
        viewModelScope.launch {
            if (nickname.isBlank()) {
                _addResult.value = AddResult.Error("별명을 입력해주세요")
                return@launch
            }

            if (phoneNumber.isBlank()) {
                _addResult.value = AddResult.Error("전화번호를 입력해주세요")
                return@launch
            }

            val contact = Contact(
                nickname = nickname.trim(),
                phoneNumber = phoneNumber.trim(),
                imageUri = imageUri
            )

            val success = repository.addContact(contact)
            if (success) {
                _addResult.value = AddResult.Success
            } else {
                _addResult.value = AddResult.Error("최대 10명까지만 등록할 수 있습니다")
            }
        }
    }

    fun resetResult() {
        _addResult.value = null
    }

    sealed class AddResult {
        object Success : AddResult()
        data class Error(val message: String) : AddResult()
    }
}

