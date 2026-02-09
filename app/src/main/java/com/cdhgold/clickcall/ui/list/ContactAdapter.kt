package com.cdhgold.clickcall.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cdhgold.clickcall.data.Contact
import com.cdhgold.clickcall.databinding.ItemContactBinding
import com.cdhgold.clickcall.util.loadContactImage

class ContactAdapter(
    private val onItemClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding, onItemClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ContactViewHolder(
        private val binding: ItemContactBinding,
        private val onItemClick: (Contact) -> Unit,
        private val onDeleteClick: (Contact) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.apply {
                tvNickname.text = contact.nickname
                tvPhoneNumber.text = contact.phoneNumber
                ivContact.loadContactImage(contact.imageUri)

                root.setOnClickListener {
                    onItemClick(contact)
                }

                btnDelete.setOnClickListener {
                    onDeleteClick(contact)
                }
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}

