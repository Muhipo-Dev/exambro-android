package com.muhipo.exambrowser.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.muhipo.exambrowser.databinding.ItemWhitelistDomainBinding

class WhitelistAdapter(
    private val domains: MutableList<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<WhitelistAdapter.DomainViewHolder>() {

    inner class DomainViewHolder(private val binding: ItemWhitelistDomainBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(domain: String) {
            binding.tvDomainName.text = domain
            binding.btnDeleteDomain.setOnClickListener {
                onDeleteClick(domain)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainViewHolder {
        val binding = ItemWhitelistDomainBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DomainViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DomainViewHolder, position: Int) {
        holder.bind(domains[position])
    }

    override fun getItemCount(): Int = domains.size

    fun updateDomains(newDomains: List<String>) {
        domains.clear()
        domains.addAll(newDomains)
        notifyDataSetChanged()
    }
}
