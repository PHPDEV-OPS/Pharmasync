package com.example.pharmasync.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.data.model.PharmacyMedicine
import com.example.pharmasync.util.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer

    private val _pharmacies = MutableStateFlow<List<Pharmacy>>(emptyList())
    val pharmacies: StateFlow<List<Pharmacy>> = _pharmacies

    private val _medicines = MutableStateFlow<List<PharmacyMedicine>>(emptyList())
    val medicines: StateFlow<List<PharmacyMedicine>> = _medicines

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** Why data couldn't be loaded (backend setup, rules, connectivity), shown on every tab. */
    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    /** Pharmacy the map should centre on, set from the list screens. */
    private val _focus = MutableStateFlow<String?>(null)
    val focus: StateFlow<String?> = _focus

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        if (_loading.value) return@launch
        _loading.value = true
        val result = container.exploreRepository.load()
        _pharmacies.value = result.pharmacies
        _medicines.value = result.medicines
        _error.value = result.error?.let { UiMessage.Error(it) }
        _loading.value = false
    }

    fun focusOn(uid: String?) {
        _focus.value = uid
    }
}
