package com.example.pharmasync.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.pharmasync.R
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.ui.common.CollectionScreen
import com.example.pharmasync.ui.common.ContactAdapter
import com.example.pharmasync.ui.common.ContactRow
import com.example.pharmasync.ui.common.MedicineAdapter
import com.example.pharmasync.ui.common.MedicineRow
import com.example.pharmasync.util.UiMessage
import com.example.pharmasync.util.collectWhileViewStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

private data class ListState<T>(val items: List<T>, val query: String, val loading: Boolean, val error: UiMessage?)

/** Shared scaffolding for the public list tabs. */
abstract class ExploreListFragment : Fragment() {
    private var _binding: FragmentCollectionBinding? = null
    protected val binding get() = _binding!!
    protected val viewModel: ExploreViewModel by activityViewModels()
    protected val explore get() = requireActivity() as ExploreActivity
    protected val query = MutableStateFlow("")
    protected lateinit var screen: CollectionScreen

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        screen = CollectionScreen(binding)
        screen.search(getString(R.string.search_hint)) { query.value = it }
        binding.swipeRefresh.isEnabled = true
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        collectWhileViewStarted(viewModel.loading) { loading ->
            if (!loading) binding.swipeRefresh.isRefreshing = false
            screen.loading(loading && !binding.swipeRefresh.isRefreshing)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PharmaciesFragment : ExploreListFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screen.header(getString(R.string.explore_pharmacies_title), getString(R.string.explore_pharmacies_subtitle))
        val adapter = ContactAdapter(
            onClick = { row -> viewModel.pharmacies.value.firstOrNull { it.uid == row.id }?.let(explore::showOnMap) },
            onViewMap = { row -> viewModel.pharmacies.value.firstOrNull { it.uid == row.id }?.let(explore::showOnMap) },
            onDirections = { row -> viewModel.pharmacies.value.firstOrNull { it.uid == row.id }?.let(explore::openDirections) },
        )
        binding.recycler.adapter = adapter

        collectWhileViewStarted(combine(viewModel.pharmacies, query, viewModel.loading, viewModel.error) { p, q, l, e -> ListState(p, q, l, e) }) { (pharmacies, q, loading, error) ->
            val visible = pharmacies.filter { q.isEmpty() || it.name.contains(q, true) || it.address.contains(q, true) }
            adapter.submitList(visible.map { ContactRow(it.uid, it.name, it.address, it.phone, it.photoUrl, showMapActions = true) })
            when {
                loading && pharmacies.isEmpty() -> screen.empty(visible = false)
                pharmacies.isEmpty() -> screen.empty(
                    visible = true,
                    icon = if (error != null) R.drawable.ic_warning else R.drawable.ic_store,
                    title = getString(if (error != null) R.string.error_load_title else R.string.empty_pharmacies_title),
                    message = error?.resolve(requireContext()) ?: getString(R.string.empty_pharmacies_message),
                    actionText = getString(R.string.action_retry),
                    action = { viewModel.refresh() },
                )
                visible.isEmpty() -> screen.empty(true, R.drawable.ic_search, getString(R.string.empty_search_title), getString(R.string.empty_search_message))
                else -> screen.empty(visible = false)
            }
        }
    }
}

class PublicMedicinesFragment : ExploreListFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screen.header(getString(R.string.explore_medicines_title), getString(R.string.explore_medicines_subtitle))
        val showPharmacy: (MedicineRow) -> Unit = { row ->
            viewModel.pharmacies.value.firstOrNull { it.uid == row.medicine.ownerId }?.let(explore::showOnMap)
        }
        val adapter = MedicineAdapter(MedicineAdapter.Mode.LOCATE, onClick = showPharmacy, onAction = showPharmacy)
        binding.recycler.adapter = adapter

        collectWhileViewStarted(combine(viewModel.medicines, query, viewModel.loading, viewModel.error) { m, q, l, e -> ListState(m, q, l, e) }) { (medicines, q, loading, error) ->
            val visible = medicines.filter {
                q.isEmpty() || it.medicine.name.contains(q, true) || it.medicine.category.contains(q, true)
            }
            adapter.submitList(visible.map {
                MedicineRow(it.medicine, subtitle = it.pharmacy?.let { p -> getString(R.string.available_at, p.name) })
            })
            when {
                loading && medicines.isEmpty() -> screen.empty(visible = false)
                medicines.isEmpty() && error != null -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_warning,
                    title = getString(R.string.error_load_title),
                    message = error.resolve(requireContext()),
                    actionText = getString(R.string.action_retry),
                    action = { viewModel.refresh() },
                )
                visible.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_medical_services,
                    title = getString(R.string.empty_public_medicines_title),
                    message = getString(R.string.empty_public_medicines_message),
                )
                else -> screen.empty(visible = false)
            }
        }
    }
}
