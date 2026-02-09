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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted, can make calls
            } else {
                Toast.makeText(
                    requireContext(),
                    "전화 권한이 필요합니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
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

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = ContactAdapter(
            onItemClick = { contact ->
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    callManager.makeCall(contact.phoneNumber)
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            },
            onDeleteClick = { contact ->
                viewModel.deleteContact(contact)
                Toast.makeText(
                    requireContext(),
                    "연락처가 삭제되었습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactListFragment.adapter
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

    private fun navigateToAddContact() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AddContactFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
