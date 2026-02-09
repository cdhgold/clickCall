package com.cdhgold.clickcall.ui.add

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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


    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri.toString()
                binding.ivContactImage.loadContactImage(selectedImageUri)
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pickImage()
            } else {
                Toast.makeText(
                    requireContext(),
                    "저장소 권한이 필요합니다",
                    Toast.LENGTH_SHORT
                ).show()
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

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnSelectImage.setOnClickListener {
            requestStoragePermissionAndPickImage()
        }

        binding.btnSave.setOnClickListener {
            saveContact()
        }

        binding.btnCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Use toolbar navigation icon if available
        val toolbar = binding.root.findViewWithTag<View?>("toolbar")
        toolbar?.let {
            it.setOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.addResult.collect { result ->
                when (result) {
                    is AddContactViewModel.AddResult.Success -> {
                        Toast.makeText(
                            requireContext(),
                            "연락처가 추가되었습니다",
                            Toast.LENGTH_SHORT
                        ).show()
                        parentFragmentManager.popBackStack()
                    }
                    is AddContactViewModel.AddResult.Error -> {
                        Toast.makeText(
                            requireContext(),
                            result.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    null -> {}
                }
                viewModel.resetResult()
            }
        }
    }

    private fun requestStoragePermissionAndPickImage() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pickImage()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    private fun saveContact() {
        val nickname = binding.etNickname.text.toString().trim()
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()

        viewModel.addContact(nickname, phoneNumber, selectedImageUri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


