package com.example.pharmasync.ui.session

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.data.model.UserProfile
import com.example.pharmasync.data.remote.ApiException
import com.example.pharmasync.ui.common.OrderAction
import com.example.pharmasync.util.InsufficientStockException
import com.example.pharmasync.util.Notifications
import com.example.pharmasync.util.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Everything a signed-in pharmacist or supplier works with. Scoped to the home activity so all tabs
 * share one data set. Screens read from Room; [refresh] pulls from the backend, and a poll keeps
 * orders and stock current while the app is in the foreground.
 */
class SessionViewModel(application: Application, val role: Role) : AndroidViewModel(application) {

    enum class Sync { PROFILE, STOCK, ORDERS, SUPPLIERS, INVOICES }

    private val app = application
    private val container = application.appContainer
    private val inventory = container.inventoryRepository
    private val ordersRepo = container.orderRepository

    val uid: String = container.auth.currentUser?.uid.orEmpty()
    val isSignedIn: Boolean get() = uid.isNotEmpty()
    val stockCollection = if (role == Role.PHARMACIST) StockCollection.INVENTORY else StockCollection.SUPPLIER_CATALOG

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val _syncing = MutableStateFlow<Set<Sync>>(emptySet())
    val syncing: StateFlow<Set<Sync>> = _syncing

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** Profile photo upload progress (0–100), or null when idle. */
    private val _uploadProgress = MutableStateFlow<Int?>(null)
    val uploadProgress: StateFlow<Int?> = _uploadProgress

    /** Set when the backend can't be reached, so screens can explain empty lists. */
    private val _backendIssue = MutableStateFlow<UiMessage?>(null)
    val backendIssue: StateFlow<UiMessage?> = _backendIssue

    /** True when the account has no Pharmasync profile yet; the UI sends the user to finish setup. */
    private val _profileMissing = MutableStateFlow(false)
    val profileMissing: StateFlow<Boolean> = _profileMissing

    val profile: StateFlow<UserProfile?> = container.userRepository.observe(uid).eager(null)
    val stock: StateFlow<List<Medicine>> = inventory.observe(uid, stockCollection).eager(emptyList())
    val orders: StateFlow<List<Order>> = ordersRepo.observe(uid, role).eager(emptyList())
    val suppliers: StateFlow<List<SupplierSummary>> =
        (if (role == Role.PHARMACIST) container.supplierRepository.observe() else emptyFlow()).eager(emptyList())
    val invoices: StateFlow<List<Invoice>> =
        (if (role == Role.PHARMACIST) container.invoiceRepository.observe(uid) else emptyFlow()).eager(emptyList())

    private var pollJob: Job? = null

    init {
        if (isSignedIn) {
            refreshAll()
            watchAlerts()
        }
    }

    // region Sync

    /** Called by the host activity so polling only runs while the app is on screen. */
    fun setForeground(active: Boolean) {
        pollJob?.cancel()
        if (!active || !isSignedIn) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refresh(Sync.ORDERS, Sync.STOCK, quiet = true)
            }
        }
    }

    fun refreshSuppliers(onDone: () -> Unit = {}) = refresh(Sync.SUPPLIERS, onDone = onDone)

    fun refreshAll(onDone: () -> Unit = {}) = refresh(*Sync.entries.toTypedArray(), onDone = onDone)

    fun refresh(vararg parts: Sync, quiet: Boolean = false, onDone: () -> Unit = {}) {
        if (!isSignedIn) return
        viewModelScope.launch {
            val wanted = parts.filter { it.appliesTo(role) }
            _syncing.update { it + wanted }
            try {
                supervisorScope {
                    wanted.forEach { part ->
                        launch {
                            try {
                                load(part)
                                _syncing.update { it - part }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                _syncing.update { it - part }
                                report(e, quiet)
                            }
                        }
                    }
                }
                if (_syncing.value.isEmpty()) _backendIssue.value = null
            } finally {
                _syncing.update { it - wanted.toSet() }
                onDone()
            }
        }
    }

    private suspend fun load(part: Sync) = when (part) {
        Sync.PROFILE -> {
            val fetched = container.userRepository.refresh()
            _profileMissing.value = fetched == null
            fetched?.let { container.session.cacheRole(uid, it.role) }
            Unit
        }
        Sync.STOCK -> inventory.refresh(uid, stockCollection)
        Sync.ORDERS -> ordersRepo.refresh(uid, role)
        Sync.SUPPLIERS -> container.supplierRepository.refresh()
        Sync.INVOICES -> container.invoiceRepository.refresh(uid)
    }

    private fun report(error: Exception, quiet: Boolean) {
        Log.w(TAG, "Refresh failed", error)
        if (error is ApiException && error.isProfileMissing) {
            _profileMissing.value = true
            return
        }
        _backendIssue.value = UiMessage.Error(error)
        if (!quiet) send(UiMessage.Error(error))
    }

    private fun Sync.appliesTo(role: Role): Boolean =
        role == Role.PHARMACIST || (this != Sync.SUPPLIERS && this != Sync.INVOICES)

    override fun onCleared() {
        pollJob?.cancel()
    }

    // endregion

    // region Alerts

    private fun watchAlerts() = viewModelScope.launch {
        syncing.first { Sync.STOCK !in it && Sync.ORDERS !in it }
        val session = container.session
        // On the very first session we only record what exists, so old events don't all notify at once.
        var seedOnly = !session.alertsSeeded(uid)
        combine(stock, orders) { s, o -> s to o }.collect { (items, orderList) ->
            items.forEach { item ->
                val key = "low:${item.id}"
                if (item.isLowStock) {
                    if (session.markAlert(uid, key, seedOnly)) Notifications.lowStock(app, item)
                } else {
                    session.clearAlert(uid, key)
                }
            }
            orderList.forEach { order ->
                when (role) {
                    Role.SUPPLIER -> if (order.status == OrderStatus.PENDING &&
                        session.markAlert(uid, "order:${order.id}:new", seedOnly)
                    ) Notifications.newOrder(app, order)
                    Role.PHARMACIST -> if (order.status in NOTIFY_PHARMACIST &&
                        session.markAlert(uid, "order:${order.id}:${order.status.name}", seedOnly)
                    ) Notifications.orderUpdate(app, order, app.getString(statusLabel(order.status)))
                }
            }
            if (seedOnly) {
                session.markAlertsSeeded(uid)
                seedOnly = false
            }
        }
    }

    // endregion

    // region Stock

    fun newMedicineId(): String = inventory.newMedicineId()

    fun saveMedicine(medicine: Medicine, image: Uri?) = launchAction(showBusy = true) {
        val saved = inventory.save(medicine, stockCollection, image)
        send(UiMessage.of(R.string.msg_medicine_saved, saved.name))
    }

    fun updateStock(medicine: Medicine, newStock: Int) = launchAction {
        inventory.updateStock(medicine, stockCollection, newStock, medicine.lowStockThreshold)
        send(UiMessage.of(R.string.msg_stock_updated))
    }

    fun deleteMedicine(medicine: Medicine) = launchAction {
        inventory.delete(medicine, stockCollection)
        send(UiMessage.of(R.string.msg_medicine_deleted, medicine.name))
    }

    fun importDemoData() = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        send(UiMessage.of(R.string.msg_importing_demo))
        try {
            val count = inventory.importDemo(uid, stockCollection)
            send(if (count > 0) UiMessage.of(R.string.msg_demo_imported, count) else UiMessage.of(R.string.msg_demo_failed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Demo import failed", e)
            send(UiMessage.Error(e))
        } finally {
            _busy.value = false
        }
    }

    // endregion

    // region Orders

    fun performOrderAction(order: Order, action: OrderAction) = launchAction(showBusy = true) {
        try {
            val updated = ordersRepo.perform(order, action.apiValue)
            if (action == OrderAction.RECEIVE) {
                send(UiMessage.of(R.string.msg_order_received, order.quantity, order.medicineName))
                inventory.refresh(uid, stockCollection)
            } else {
                send(UiMessage.of(R.string.msg_order_status, app.getString(statusLabel(updated.status)).lowercase()))
            }
        } catch (e: InsufficientStockException) {
            send(UiMessage.of(R.string.error_insufficient_stock, e.available))
            inventory.refresh(uid, stockCollection)
        }
    }

    // endregion

    // region Invoices

    fun addInvoice(name: String, description: String, image: Uri) = launchAction(showBusy = true) {
        container.invoiceRepository.add(uid, name, description, image)
        send(UiMessage.of(R.string.msg_invoice_saved))
    }

    // endregion

    // region Profile

    fun uploadProfilePhoto(image: Uri) = viewModelScope.launch {
        if (_uploadProgress.value != null) return@launch
        _uploadProgress.value = 0
        try {
            container.userRepository.uploadPhoto(image) { _uploadProgress.value = it }
            send(UiMessage.of(R.string.msg_photo_updated))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Profile photo upload failed", e)
            send(UiMessage.Error(e))
        } finally {
            _uploadProgress.value = null
        }
    }

    fun updateProfile(name: String, businessName: String, address: String, phone: String) = launchAction(showBusy = true) {
        container.userRepository.createOrUpdate(null, name, businessName, address, phone)
        send(UiMessage.of(R.string.msg_profile_updated))
    }

    fun pinCurrentLocation() = launchAction(showBusy = true) {
        val locationRepo = container.locationRepository
        send(UiMessage.of(R.string.msg_fetching_location))
        val point = locationRepo.currentLocation()
        if (point == null) {
            send(UiMessage.of(R.string.error_location_unavailable))
            return@launchAction
        }
        val address = locationRepo.reverseGeocode(point) ?: profile.value?.address.orEmpty()
        container.userRepository.saveLocation(point, address)
        send(UiMessage.of(R.string.msg_location_updated, address))
    }

    fun sendPasswordReset() = launchAction {
        val email = profile.value?.email?.ifBlank { null } ?: container.auth.currentUser?.email ?: return@launchAction
        container.authRepository.sendPasswordReset(email)
        send(UiMessage.of(R.string.msg_reset_sent, email))
    }

    suspend fun signOut() {
        pollJob?.cancel()
        container.authRepository.signOut()
    }

    // endregion

    private fun launchAction(showBusy: Boolean = false, block: suspend () -> Unit) = viewModelScope.launch {
        if (showBusy) _busy.value = true
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Action failed", e)
            send(UiMessage.Error(e))
        } finally {
            if (showBusy) _busy.value = false
        }
    }

    private fun send(message: UiMessage) {
        messageChannel.trySend(message)
    }

    private fun <T> Flow<T>.eager(initial: T): StateFlow<T> =
        if (isSignedIn) stateIn(viewModelScope, SharingStarted.Eagerly, initial) else MutableStateFlow(initial)

    companion object {
        private const val TAG = "SessionViewModel"
        private const val POLL_INTERVAL_MS = 20_000L
        private val NOTIFY_PHARMACIST = setOf(OrderStatus.ACCEPTED, OrderStatus.DISPATCHED, OrderStatus.DECLINED)

        fun statusLabel(status: OrderStatus): Int = when (status) {
            OrderStatus.PENDING -> R.string.status_pending
            OrderStatus.ACCEPTED -> R.string.status_accepted
            OrderStatus.DISPATCHED -> R.string.status_dispatched
            OrderStatus.DELIVERED -> R.string.status_delivered
            OrderStatus.DECLINED -> R.string.status_declined
            OrderStatus.CANCELLED -> R.string.status_cancelled
        }

        fun factory(application: Application, role: Role): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionViewModel(application, role) }
        }
    }
}
