package com.cdhgold.clickcall.ui.list

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cdhgold.clickcall.data.Contact
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cdhgold.clickcall.R
import com.cdhgold.clickcall.data.ContactRepository
import com.cdhgold.clickcall.databinding.FragmentContactListBinding
import com.cdhgold.clickcall.ui.add.AddContactFragment
import com.cdhgold.clickcall.util.CallManager
import kotlinx.coroutines.launch

class ContactListFragment : Fragment() {

    private var _binding: FragmentContactListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ContactAdapter
    private lateinit var repository: ContactRepository
    private lateinit var callManager: CallManager
    private lateinit var viewModel: ContactListViewModel

    private var pendingPhoneNumber: String? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingPhoneNumber?.let { callManager.makeCall(it) }
            } else {
                Toast.makeText(requireContext(), R.string.permission_call_denied, Toast.LENGTH_SHORT).show()
            }
            pendingPhoneNumber = null
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            viewModel.exportContacts(uri) { success ->
                val msgRes = if (success) R.string.export_success else R.string.export_failed
                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
            }
        }

    private var pendingImportUri: android.net.Uri? = null

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            pendingImportUri = uri
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.import_confirm_title)
                .setMessage(R.string.import_confirm_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_confirm) { _, _ ->
                    pendingImportUri?.let { importUri ->
                        viewModel.importContacts(importUri) { success ->
                            val msgRes = if (success) R.string.import_success else R.string.import_failed
                            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingImportUri = null
                }
                .show()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = ContactRepository(requireContext())
        viewModel = ContactListViewModel(repository)
        callManager = CallManager(requireContext())

        setupToolbarMenu()
        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupToolbarMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_contact_list)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_export -> {
                    exportLauncher.launch("clickcall_contacts.json")
                    true
                }
                R.id.action_import -> {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactAdapter(
            onCallClick = { contact ->
                makeCallWithPermission(contact.phoneNumber)
            },
            onEditClick = { contact ->
                navigateToEditContact(contact.id)
            },
            onDeleteClick = { contact ->
                showDeleteConfirmDialog(contact)
            }
        )

        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactListFragment.adapter
        }
    }

    private fun makeCallWithPermission(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            callManager.makeCall(phoneNumber)
        } else {
            pendingPhoneNumber = phoneNumber
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allContacts.collect { contacts ->
                adapter.submitList(contacts)

                if (contacts.isEmpty()) {
                    binding.rvContacts.visibility = View.GONE
                    binding.emptyStateContainer.visibility = View.VISIBLE
                } else {
                    binding.rvContacts.visibility = View.VISIBLE
                    binding.emptyStateContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            navigateToAddContact()
        }

        binding.btnAddContactEmpty.setOnClickListener {
            navigateToAddContact()
        }
    }

    private fun showDeleteConfirmDialog(contact: Contact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(R.string.button_delete) { _, _ ->
                viewModel.deleteContact(contact)
                Toast.makeText(requireContext(), R.string.contact_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun navigateToAddContact() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AddContactFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToEditContact(contactId: Int) {
        val fragment = AddContactFragment().apply {
            arguments = Bundle().apply {
                putInt("contact_id", contactId)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
