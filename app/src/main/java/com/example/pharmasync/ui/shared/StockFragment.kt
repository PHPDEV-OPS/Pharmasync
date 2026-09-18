package com.example.pharmasync.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.databinding.SheetMedicineDetailBinding
import com.example.pharmasync.ui.common.CollectionScreen
import com.example.pharmasync.ui.common.Dialogs
import com.example.pharmasync.ui.common.MedicineAdapter
import com.example.pharmasync.ui.common.MedicineEditor
import com.example.pharmasync.ui.common.MedicineRow
import com.example.pharmasync.ui.common.bindStock
import com.example.pharmasync.ui.pharmacist.SupplierCatalogActivity
import com.example.pharmasync.ui.session.SessionFragment
import com.example.pharmasync.ui.session.SessionViewModel
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.ImageLoader
import com.example.pharmasync.util.collectWhileViewStarted
import com.example.pharmasync.util.visibleIf
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Pharmacy inventory or supplier catalog, depending on the signed-in role. */
class StockFragment : SessionFragment() {

    private enum class Filter { ALL, LOW, OUT }

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(Filter.ALL)

    private val editor = MedicineEditor(this) { medicine, image -> session.saveMedicine(medicine, image) }

    private val isPharmacist get() = session.role == Role.PHARMACIST

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val screen = CollectionScreen(binding)
        val adapter = MedicineAdapter(
            mode = MedicineAdapter.Mode.MANAGE,
            onClick = { showDetails(it.medicine) },
            onAction = { openEditor(it.medicine) },
        )
        binding.recycler.adapter = adapter

        screen.headerAction(R.drawable.ic_cloud_download, getString(R.string.action_import_demo)) { session.importDemoData() }
        screen.search(getString(R.string.search_hint)) { query.value = it }
        screen.filters(
            listOf(getString(R.string.filter_all), getString(R.string.filter_low_stock), getString(R.string.filter_out_of_stock)),
            selected = filter.value.ordinal,
        ) { filter.value = Filter.entries[it] }
        screen.fab(
            getString(if (isPharmacist) R.string.action_add_medicine else R.string.action_add_product),
            R.drawable.ic_add,
        ) { openEditor(null) }

        collectWhileViewStarted(session.stock) { items ->
            val low = items.count { it.isLowStock || it.isOutOfStock }
            screen.header(
                getString(if (isPharmacist) R.string.inventory_title else R.string.catalog_title),
                getString(if (isPharmacist) R.string.inventory_subtitle else R.string.catalog_subtitle, items.size, low),
            )
        }

        collectWhileViewStarted(combine(session.stock, query, filter) { items, q, f -> Triple(items, q, f) }) { (items, q, f) ->
            val visible = items
                .filter { q.isEmpty() || it.name.contains(q, true) || it.category.contains(q, true) || it.manufacturer.contains(q, true) }
                .filter {
                    when (f) {
                        Filter.ALL -> true
                        Filter.LOW -> it.isLowStock && !it.isOutOfStock
                        Filter.OUT -> it.isOutOfStock
                    }
                }
            adapter.submitList(visible.map { MedicineRow(it) })
            when {
                items.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_inventory,
                    title = getString(if (isPharmacist) R.string.empty_inventory_title else R.string.empty_catalog_title),
                    message = getString(if (isPharmacist) R.string.empty_inventory_message else R.string.empty_catalog_message),
                    actionText = getString(R.string.action_import_demo),
                    action = { session.importDemoData() },
                )
                visible.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_search,
                    title = getString(R.string.empty_search_title),
                    message = getString(R.string.empty_search_message),
                )
                else -> screen.empty(visible = false)
            }
        }

        collectWhileViewStarted(combine(session.syncing, session.busy) { s, b -> b || SessionViewModel.Sync.STOCK in s }) {
            screen.loading(it)
        }
    }

    private fun openEditor(existing: Medicine?) {
        val categories = session.stock.value.map { it.category }.filter { it.isNotBlank() }
        editor.open(existing, session.uid, session::newMedicineId, categories)
    }

    private fun showDetails(item: Medicine) {
        val context = requireContext()
        val sheet = BottomSheetDialog(context)
        val details = SheetMedicineDetailBinding.inflate(layoutInflater)
        with(details) {
            name.text = item.name
            category.text = item.category
            stockPill.bindStock(item)
            price.text = Formatters.money(item.price)
            stock.text = item.stock.toString()
            alertLevel.text = if (item.lowStockThreshold > 0) item.lowStockThreshold.toString() else "—"
            manufacturer.text = item.manufacturer
            manufacturer.visibleIf(item.manufacturer.isNotBlank())
            description.text = item.description
            description.visibleIf(item.description.isNotBlank())
            ImageLoader.load(image, item.imageRef)

            btnUpdateStock.setOnClickListener {
                sheet.dismiss()
                Dialogs.number(
                    context,
                    title = getString(R.string.dialog_adjust_stock_title),
                    hint = getString(R.string.label_stock),
                    initial = item.stock,
                    confirmText = getString(R.string.action_save),
                    validate = { if (it < 0) getString(R.string.error_invalid_number) else null },
                ) { session.updateStock(item, it) }
            }
            btnReorder.visibleIf(isPharmacist)
            btnReorder.setOnClickListener {
                sheet.dismiss()
                chooseSupplierToReorder(item)
            }
            btnEdit.setOnClickListener {
                sheet.dismiss()
                openEditor(item)
            }
            btnDelete.setOnClickListener {
                sheet.dismiss()
                Dialogs.confirm(
                    context,
                    title = getString(R.string.dialog_delete_title, item.name),
                    message = getString(R.string.dialog_delete_message),
                    confirmText = getString(R.string.action_delete),
                ) { session.deleteMedicine(item) }
            }
        }
        sheet.setContentView(details.root)
        sheet.show()
    }

    private fun chooseSupplierToReorder(item: Medicine, refreshed: Boolean = false) {
        val suppliers = session.suppliers.value
        if (suppliers.isEmpty() && !refreshed) {
            // The cache may be stale; ask the server before telling the user there are none.
            session.refreshSuppliers { if (isAdded) chooseSupplierToReorder(item, refreshed = true) }
            return
        }
        if (suppliers.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.empty_suppliers_title)
                .setMessage(session.backendIssue.value?.resolve(requireContext()) ?: getString(R.string.msg_no_suppliers_yet))
                .setPositiveButton(R.string.action_ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_pick_supplier)
            .setItems(suppliers.map { it.name }.toTypedArray()) { _, index ->
                startActivity(SupplierCatalogActivity.intent(requireContext(), suppliers[index].uid, item.name))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
