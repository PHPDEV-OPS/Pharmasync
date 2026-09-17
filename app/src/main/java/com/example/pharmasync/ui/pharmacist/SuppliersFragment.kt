package com.example.pharmasync.ui.pharmacist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pharmasync.R
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.ui.common.CollectionScreen
import com.example.pharmasync.ui.common.ContactAdapter
import com.example.pharmasync.ui.common.ContactRow
import com.example.pharmasync.ui.session.SessionFragment
import com.example.pharmasync.ui.session.SessionViewModel
import com.example.pharmasync.util.collectWhileViewStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Registered supplier accounts; tapping one opens its catalog for ordering. */
class SuppliersFragment : SessionFragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!
    private val query = MutableStateFlow("")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val screen = CollectionScreen(binding)
        screen.header(getString(R.string.suppliers_title), getString(R.string.suppliers_subtitle))
        screen.search(getString(R.string.search_hint)) { query.value = it }

        val adapter = ContactAdapter(onClick = { row ->
            startActivity(SupplierCatalogActivity.intent(requireContext(), row.id))
        })
        binding.recycler.adapter = adapter

        collectWhileViewStarted(combine(session.suppliers, query) { s, q -> s to q }) { (suppliers, q) ->
            val visible = suppliers.filter { q.isEmpty() || it.name.contains(q, true) || it.address.contains(q, true) }
            adapter.submitList(visible.map { ContactRow(it.uid, it.name, it.address, listOf(it.email, it.phone).filter(String::isNotBlank).joinToString(" · "), it.photoUrl, false) })
            when {
                suppliers.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_local_shipping,
                    title = getString(R.string.empty_suppliers_title),
                    message = getString(R.string.empty_suppliers_message),
                )
                visible.isEmpty() -> screen.empty(true, R.drawable.ic_search, getString(R.string.empty_search_title), getString(R.string.empty_search_message))
                else -> screen.empty(visible = false)
            }
        }
        collectWhileViewStarted(session.syncing) { screen.loading(SessionViewModel.Sync.SUPPLIERS in it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
