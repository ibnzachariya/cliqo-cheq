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
    val events: List<AppOpenEvent> = emptyList()
)

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UsageRepository(app.applicationContext)

    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    /**
     * Re-checks permission and, if granted, reloads the event list.
     * Call this from onResume so the screen refreshes after the user returns
     * from the Settings "Usage access" screen.
     */
    fun refresh() {
        val granted = repo.hasUsageAccess()
        _state.value = _state.value.copy(hasPermission = granted)
        if (!granted) return

        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) { repo.getAppOpenEvents() }
            _state.value = _state.value.copy(loading = false, events = events)
        }
    }

    fun settingsIntent() = repo.usageAccessSettingsIntent()
}
