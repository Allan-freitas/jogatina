package br.com.jogatina.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.notifications.NotificationDto
import br.com.jogatina.data.notifications.NotificationsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<NotificationDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val onlyUnread: Boolean = false,
    val markingAll: Boolean = false
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
    val visible: List<NotificationDto> get() =
        if (onlyUnread) notifications.filter { !it.isRead } else notifications
}

class NotificationsViewModel(
    private val repo: NotificationsRepository,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh(first = true)
    }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(loading = first, refreshing = !first, error = null)
        viewModelScope.launch {
            when (val r = repo.getNotifications()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        notifications = r.value, loading = false, refreshing = false
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, refreshing = false)
                    handleError(r)
                }
            }
        }
    }

    fun setOnlyUnread(onlyUnread: Boolean) {
        _state.value = _state.value.copy(onlyUnread = onlyUnread)
    }

    fun open(notification: NotificationDto) {
        if (notification.isRead) return
        viewModelScope.launch {
            when (val r = repo.markAsRead(notification.id)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        notifications = _state.value.notifications.map {
                            if (it.id == notification.id) it.copy(isRead = true) else it
                        }
                    )
                is AuthResult.Error -> handleError(r)
            }
        }
    }

    fun markAllAsRead() {
        if (_state.value.markingAll) return
        _state.value = _state.value.copy(markingAll = true)
        viewModelScope.launch {
            when (val r = repo.markAllAsRead()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        notifications = _state.value.notifications.map { it.copy(isRead = true) },
                        markingAll = false
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(markingAll = false)
                    handleError(r)
                }
            }
        }
    }

    fun startAutoRefresh() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (!_state.value.loading && !_state.value.refreshing) refresh()
            }
        }
    }

    fun stopAutoRefresh() {
        pollJob?.cancel()
        pollJob = null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun handleError(error: AuthResult.Error) {
        if (error.statusCode == 401) onAuthExpired()
        else _state.value = _state.value.copy(error = error.message)
    }

    companion object {
        const val POLL_INTERVAL_MS = 60_000L

        fun factory(
            repo: NotificationsRepository,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotificationsViewModel(repo, onAuthExpired) as T
            }
    }
}
