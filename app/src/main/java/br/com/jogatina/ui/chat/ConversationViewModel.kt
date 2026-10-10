package br.com.jogatina.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.TokenStore
import br.com.jogatina.data.chat.ChatEvent
import br.com.jogatina.data.chat.ChatMessageDto
import br.com.jogatina.data.chat.ChatRepository
import br.com.jogatina.data.chat.ChatSocket
import br.com.jogatina.data.social.FriendDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationUiState(
    val messages: List<ChatMessageDto> = emptyList(),
    val loadingHistory: Boolean = true,
    val error: String? = null,
    val input: String = "",
    val sending: Boolean = false,
    val connected: Boolean = false,
    val connectionInfo: String = "Conectando..."
)

class ConversationViewModel(
    private val chat: ChatRepository,
    private val tokens: TokenStore,
    private val baseUrl: String,
    val myUserId: String?,
    initialConversationId: String?,
    private val recipient: FriendDto?,
    private val onAuthExpired: () -> Unit,
    private val onConversationOpened: (String) -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var conversationId: String? = initialConversationId
    private val socket = ChatSocket(baseUrl, viewModelScope)
    private var eventsJob: Job? = null

    init {
        val token = tokens.accessToken
        if (token.isNullOrBlank()) {
            onAuthExpired()
        } else {
            socket.connect(token)
            eventsJob = viewModelScope.launch {
                socket.events.collect { event -> onEvent(event) }
            }
            val id = conversationId
            if (id != null) {
                loadHistory(id)
                socket.markRead(id)
            } else {
                _state.value = _state.value.copy(loadingHistory = false)
            }
        }
    }

    fun onInputChange(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isBlank() || _state.value.sending) return
        if (!socket.isConnected) {
            _state.value = _state.value.copy(error = "Sem conexão. Aguarde reconectar.")
            return
        }
        _state.value = _state.value.copy(sending = true)
        val id = conversationId
        val ok = if (id != null) {
            socket.sendMessage(conversationId = id, recipientId = null, content = text)
        } else {
            val r = recipient
            if (r == null) false
            else socket.sendMessage(conversationId = null, recipientId = r.friendId, content = text)
        }
        if (!ok) {
            _state.value = _state.value.copy(sending = false, error = "Não foi possível enviar.")
            return
        }
        _state.value = _state.value.copy(input = "", sending = false)
        if (conversationId == null && recipient != null) {
            resolveNewConversation(recipient.friendId)
        }
    }

    fun retry() {
        conversationId?.let { loadHistory(it) }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun loadHistory(id: String) {
        _state.value = _state.value.copy(loadingHistory = true, error = null)
        viewModelScope.launch {
            when (val r = chat.getMessages(id)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        messages = merge(_state.value.messages, r.value),
                        loadingHistory = false
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loadingHistory = false)
                    handleError(r)
                }
            }
        }
    }

    /** Após o primeiro envio com recipientId, descobre o id da conversa criada. */
    private fun resolveNewConversation(friendId: String) {
        viewModelScope.launch {
            repeat(10) {
                delay(600)
                when (val r = chat.getConversations()) {
                    is AuthResult.Success -> {
                        val found = r.value.firstOrNull { c ->
                            c.participants.any { it.userId == friendId }
                        }
                        if (found != null) {
                            conversationId = found.conversationId
                            onConversationOpened(found.conversationId)
                            loadHistory(found.conversationId)
                            socket.markRead(found.conversationId)
                            return@launch
                        }
                    }
                    is AuthResult.Error -> {
                        handleError(r)
                        return@launch
                    }
                }
            }
        }
    }

    private fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.Connected ->
                _state.value = _state.value.copy(connected = true, connectionInfo = "Online")
            is ChatEvent.Disconnected ->
                _state.value = _state.value.copy(connected = false, connectionInfo = "Reconectando...")
            is ChatEvent.Message -> {
                if (event.message.conversationId == conversationId) {
                    _state.value = _state.value.copy(
                        messages = merge(_state.value.messages, listOf(event.message))
                    )
                    socket.markRead(event.message.conversationId)
                }
            }
            is ChatEvent.ReadOk -> Unit
            is ChatEvent.Error ->
                _state.value = _state.value.copy(error = event.message)
        }
    }

    private fun merge(current: List<ChatMessageDto>, incoming: List<ChatMessageDto>): List<ChatMessageDto> =
        (current + incoming)
            .distinctBy { it.id }
            .sortedBy { it.sentOnUtc }

    private fun handleError(error: AuthResult.Error) {
        if (error.statusCode == 401) onAuthExpired()
        else _state.value = _state.value.copy(error = error.message)
    }

    override fun onCleared() {
        eventsJob?.cancel()
        socket.disconnect()
    }

    companion object {
        fun factory(
            chat: ChatRepository,
            tokens: TokenStore,
            baseUrl: String,
            myUserId: String?,
            conversationId: String?,
            recipient: FriendDto?,
            onAuthExpired: () -> Unit,
            onConversationOpened: (String) -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ConversationViewModel(
                        chat, tokens, baseUrl, myUserId, conversationId,
                        recipient, onAuthExpired, onConversationOpened
                    ) as T
            }
    }
}
