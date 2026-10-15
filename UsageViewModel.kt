package com.example.appusagetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the usage screen. */
data class UsageUiState(
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val rows: List<AppUsageRow> = emptyList()
)

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UsageRepository(app.applicationContext)

    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    /**
     * Re-checks permission and, if granted, reloads the usage rows.
     * Called from onResume so the screen refreshes after returning from Settings.
     */
    fun refresh() {
        val granted = repo.hasUsageAccess()
        _state.value = _state.value.copy(hasPermission = granted)
        if (!granted) return

        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { repo.getUsageRows() }
            _state.value = _state.value.copy(loading = false, rows = rows)
        }
    }

    fun settingsIntent() = repo.usageAccessSettingsIntent()
}
