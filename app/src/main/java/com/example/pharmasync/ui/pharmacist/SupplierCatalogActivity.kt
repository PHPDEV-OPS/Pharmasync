package com.example.pharmasync.ui.pharmacist

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.ui.common.CollectionScreen
import com.example.pharmasync.ui.common.Dialogs
import com.example.pharmasync.ui.common.MedicineAdapter
import com.example.pharmasync.ui.common.MedicineRow
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.InsufficientStockException
import com.example.pharmasync.util.UiMessage
import com.example.pharmasync.util.collectWhileStarted
import com.example.pharmasync.util.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A supplier's catalog, from which a pharmacist places orders. */
class SupplierCatalogActivity : AppCompatActivity() {

    private val viewModel: SupplierCatalogViewModel by viewModels {
        viewModelFactory {
            initializer { SupplierCatalogViewModel(application, intent.getStringExtra(EXTRA_SUPPLIER_ID).orEmpty()) }
        }
    }
    private val query = MutableStateFlow("")
    private lateinit var binding: FragmentCollectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val screen = CollectionScreen(binding)
        screen.backButton { finish() }
        screen.header(getString(R.string.suppliers_title))
        screen.search(getString(R.string.search_hint)) { query.value = it }
        intent.getStringExtra(EXTRA_QUERY)?.takeIf { savedInstanceState == null }?.let { binding.searchInput.setText(it) }

        val adapter = MedicineAdapter(
            mode = MedicineAdapter.Mode.ORDER,
            onClick = { showOrderDialog(it.medicine) },
            onAction = { showOrderDialog(it.medicine) },
        )
        binding.recycler.adapter = adapter

        binding.swipeRefresh.isEnabled = true
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        collectWhileStarted(viewModel.loading) { if (!it) binding.swipeRefresh.isRefreshing = false }

        collectWhileStarted(viewModel.supplier) { supplier ->
            if (supplier != null) screen.header(supplier.name, supplier.address.ifBlank { supplier.email })
        }
        collectWhileStarted(combine(viewModel.catalog, query) { c, q -> c to q }) { (catalog, q) ->
            val visible = catalog.filter { q.isEmpty() || it.name.contains(q, true) || it.category.contains(q, true) }
            adapter.submitList(visible.map { MedicineRow(it) })
            when {
                catalog.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_inventory,
                    title = getString(R.string.supplier_catalog_empty_title),
                    message = getString(R.string.supplier_catalog_empty_message),
                )
                visible.isEmpty() -> screen.empty(true, R.drawable.ic_search, getString(R.string.empty_search_title), getString(R.string.empty_search_message))
                else -> screen.empty(visible = false)
            }
        }
        collectWhileStarted(viewModel.loading) { screen.loading(it) }
        collectWhileStarted(viewModel.messages) { binding.root.showMessage(it) }
    }

    private fun showOrderDialog(item: Medicine) {
        if (item.isOutOfStock) {
            binding.root.showMessage(getString(R.string.stock_out))
            return
        }
        Dialogs.number(
            this,
            title = getString(R.string.dialog_order_title, item.name),
            hint = getString(R.string.label_quantity),
            initial = null,
            confirmText = getString(R.string.action_place_order),
            helper = getString(R.string.helper_available, item.stock, Formatters.money(item.price)),
            validate = { qty ->
                when {
                    qty <= 0 -> getString(R.string.error_invalid_number)
                    qty > item.stock -> getString(R.string.error_quantity_exceeds, item.stock)
                    else -> null
                }
            },
            summary = { qty -> getString(R.string.order_total, Formatters.money(qty * item.price)) },
        ) { qty -> viewModel.placeOrder(item, qty) }
    }

    companion object {
        private const val EXTRA_SUPPLIER_ID = "supplier_id"
        private const val EXTRA_QUERY = "query"

        fun intent(context: Context, supplierId: String, query: String? = null): Intent =
            Intent(context, SupplierCatalogActivity::class.java)
                .putExtra(EXTRA_SUPPLIER_ID, supplierId)
                .putExtra(EXTRA_QUERY, query)
    }
}

class SupplierCatalogViewModel(application: Application, private val supplierId: String) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val uid = container.auth.currentUser?.uid.orEmpty()

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    val catalog: StateFlow<List<Medicine>> = container.inventoryRepository
        .observe(supplierId, StockCollection.SUPPLIER_CATALOG)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val supplier: StateFlow<SupplierSummary?> = container.supplierRepository.observe()
        .map { list -> list.firstOrNull { it.uid == supplierId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        try {
            container.inventoryRepository.refresh(supplierId, StockCollection.SUPPLIER_CATALOG, forOwner = supplierId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Catalog load failed", e)
            messageChannel.trySend(UiMessage.Error(e))
        } finally {
            _loading.value = false
        }
    }

    fun placeOrder(item: Medicine, quantity: Int) = viewModelScope.launch {
        try {
            val seller = supplier.value
            container.orderRepository.place(supplierId, item, quantity)
            messageChannel.trySend(UiMessage.of(R.string.msg_order_placed, seller?.name ?: item.name))
            refresh()
        } catch (e: InsufficientStockException) {
            messageChannel.trySend(UiMessage.of(R.string.error_insufficient_stock, e.available))
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Order failed", e)
            messageChannel.trySend(UiMessage.Error(e))
        }
    }

    private companion object {
        const val TAG = "SupplierCatalog"
    }
}
