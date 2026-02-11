package com.cdhgold.clickcall.ui.add

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cdhgold.clickcall.R
import com.cdhgold.clickcall.data.ContactRepository
import com.cdhgold.clickcall.databinding.FragmentAddContactBinding
import com.cdhgold.clickcall.util.loadContactImage
import kotlinx.coroutines.launch

class AddContactFragment : Fragment() {

    private var _binding: FragmentAddContactBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ContactRepository
    private lateinit var viewModel: AddContactViewModel
    private var selectedImageUri: String? = null

    private var editContactId: Int = -1
    private var editCreatedAt: Long = 0L
    private val isEditMode get() = editContactId != -1

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                // 영구 읽기 권한 획득
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedImageUri = uri.toString()
                binding.ivContactImage.loadContactImage(selectedImageUri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = ContactRepository(requireContext())
        viewModel = AddContactViewModel(repository)

        editContactId = arguments?.getInt("contact_id", -1) ?: -1

        setupListeners()
        setupObservers()

        if (isEditMode) {
            binding.toolbar.setTitle(R.string.edit_contact)
            viewModel.loadContact(editContactId)
        }
    }

    private fun setupListeners() {
        binding.btnSelectImage.setOnClickListener {
            pickImage()
        }

        binding.btnSave.setOnClickListener {
            saveContact()
        }

        binding.btnCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editContact.collect { contact ->
                if (contact != null) {
                    binding.etNickname.setText(contact.nickname)
                    binding.etPhoneNumber.setText(contact.phoneNumber)
                    binding.cbPriority.isChecked = contact.isPriority
                    selectedImageUri = contact.imageUri
                    editCreatedAt = contact.createdAt
                    binding.ivContactImage.loadContactImage(contact.imageUri)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.addResult.collect { result ->
                when (result) {
                    is AddContactViewModel.AddResult.Success -> {
                        val msg = if (isEditMode) R.string.contact_updated else R.string.contact_added
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                    is AddContactViewModel.AddResult.Error -> {
                        val msgRes = when (result.type) {
                            AddContactViewModel.ErrorType.EMPTY_NICKNAME -> R.string.error_empty_nickname
                            AddContactViewModel.ErrorType.EMPTY_PHONE -> R.string.error_empty_phone
                            AddContactViewModel.ErrorType.DUPLICATE_NICKNAME -> R.string.error_duplicate_nickname
                            AddContactViewModel.ErrorType.MAX_CONTACTS -> R.string.max_contacts_reached
                            AddContactViewModel.ErrorType.MAX_PRIORITY -> R.string.error_max_priority
                        }
                        Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
                    }
                    null -> {}
                }
                viewModel.resetResult()
            }
        }
    }

    private fun pickImage() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun saveContact() {
        val nickname = binding.etNickname.text.toString().trim()
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        val isPriority = binding.cbPriority.isChecked

        if (isEditMode) {
            viewModel.updateContact(editContactId, nickname, phoneNumber, selectedImageUri, isPriority, editCreatedAt)
        } else {
            viewModel.addContact(nickname, phoneNumber, selectedImageUri, isPriority)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
