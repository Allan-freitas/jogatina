package br.com.jogatina.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.chat.ChatEvent
import br.com.jogatina.data.chat.ChatSocket
import br.com.jogatina.data.notifications.NotificationDto
import br.com.jogatina.data.notifications.NotificationsRepository
import br.com.jogatina.notifications.SystemNotifier
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
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val tokenRefresher: (suspend () -> String?)?,
    private val appContext: Context,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var eventsJob: Job? = null

    /**
     * Socket vivo enquanto o usuário está logado: o servidor empurra as
     * notificações recém-criadas (convites, mensagens), então o sino atualiza
     * sozinho — sem depender de reabrir a tela ou navegar.
     */
    private val socket = ChatSocket(
        baseUrl,
        viewModelScope,
        tokenProvider = tokenProvider,
        tokenRefresher = tokenRefresher
    )

    init {
        refresh(first = true)
        startAutoRefresh()
        socket.connect()
        eventsJob = viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is ChatEvent.Notification -> {
                        onRealtimeNotification(event.notification)
                        SystemNotifier.show(
                            context = appContext,
                            id = event.notification.id,
                            title = event.notification.title.ifBlank { "Jogatina" },
                            body = event.notification.body,
                            referenceType = event.notification.referenceType,
                            referenceId = event.notification.referenceId
                        )
                    }
                    // Mensagem nova também gera notificação no servidor: recarrega o sino.
                    is ChatEvent.Message -> refresh()
                    is ChatEvent.AuthExpired -> onAuthExpired()
                    else -> Unit
                }
            }
        }
    }

    private fun onRealtimeNotification(notification: NotificationDto) {
        val current = _state.value.notifications
        if (current.any { it.id == notification.id }) return
        _state.value = _state.value.copy(
            notifications = (listOf(notification) + current).normalized()
        )
    }

    /** Deduplica por id e mantém a mais recente no topo: a lista do filtro e o contador saem da mesma fonte. */
    private fun List<NotificationDto>.normalized(): List<NotificationDto> =
        distinctBy { it.id }.sortedByDescending { it.createdOnUtc }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(loading = first, refreshing = !first, error = null)
        viewModelScope.launch {
            when (val r = repo.getNotifications()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        notifications = r.value.normalized(), loading = false, refreshing = false
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

    override fun onCleared() {
        stopAutoRefresh()
        eventsJob?.cancel()
        socket.disconnect()
    }

    companion object {
        const val POLL_INTERVAL_MS = 60_000L

        fun factory(
            repo: NotificationsRepository,
            baseUrl: String,
            tokenProvider: () -> String?,
            tokenRefresher: (suspend () -> String?)?,
            appContext: Context,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotificationsViewModel(repo, baseUrl, tokenProvider, tokenRefresher, appContext, onAuthExpired) as T
            }
    }
}
