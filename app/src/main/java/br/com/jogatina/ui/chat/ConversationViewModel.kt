package br.com.jogatina.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.R
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
    /** Enviadas ainda não confirmadas pelo servidor (exibidas com leve transparência). */
    val pending: List<PendingSend> = emptyList(),
    val loadingHistory: Boolean = true,
    val error: String? = null,
    val input: String = "",
    val sending: Boolean = false,
    /** Imagem anexada (galeria ou GIF/figurinha do teclado), ainda não enviada. */
    val pickedImage: PickedChatImage? = null,
    val uploadingImage: Boolean = false,
    val connected: Boolean = false,
    val connectionInfo: String = ""
)

/** Bytes já lidos do content:// (teclado ou galeria) + Uri de preview local. */
data class PickedChatImage(
    val bytes: ByteArray,
    val mimeType: String,
    val previewUri: Uri? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedChatImage) return false
        return mimeType == other.mimeType && previewUri == other.previewUri && bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (previewUri?.hashCode() ?: 0)
        return result
    }
}

data class PendingSend(
    val tempId: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val senderId: String?,
    val sentOnUtc: String = java.time.Instant.now().toString()
)

class ConversationViewModel(
    private val chat: ChatRepository,
    private val tokens: TokenStore,
    private val baseUrl: String,
    val myUserId: String?,
    initialConversationId: String?,
    private val recipient: FriendDto?,
    private val appContext: Context,
    private val onAuthExpired: () -> Unit,
    private val onConversationOpened: (String) -> Unit,
    tokenRefresher: (suspend () -> String?)? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        ConversationUiState(connectionInfo = appContext.getString(R.string.chat_connecting))
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var conversationId: String? = initialConversationId
    private var everConnected = false
    private val socket = ChatSocket(
        baseUrl,
        viewModelScope,
        tokenProvider = { tokens.accessToken },
        tokenRefresher = tokenRefresher
    )
    private var eventsJob: Job? = null

    init {
        if (tokens.accessToken.isNullOrBlank()) {
            onAuthExpired()
        } else {
            socket.connect()
            eventsJob = viewModelScope.launch {
                socket.events.collect { event -> onEvent(event) }
            }
            val id = conversationId
            if (id != null) {
                loadHistory(id)
                socket.markRead(id)
            } else if (recipient != null) {
                // Veio de "conversar": pode já existir conversa com esse amigo.
                viewModelScope.launch {
                    val existing = findConversation(recipient.friendId)
                    if (existing != null) {
                        conversationId = existing
                        onConversationOpened(existing)
                        loadHistory(existing)
                        socket.markRead(existing)
                    } else {
                        _state.value = _state.value.copy(loadingHistory = false)
                    }
                }
            } else {
                _state.value = _state.value.copy(loadingHistory = false)
            }
        }
    }

    fun onInputChange(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    fun onImagePicked(image: PickedChatImage?) {
        _state.value = _state.value.copy(pickedImage = image, error = null)
    }

    /** URL absoluta para exibir imagens hospedadas (content = imageUrl relativa). */
    fun fullImageUrl(relative: String?): String? = chat.imageUrl(relative)

    fun send() {
        val text = _state.value.input.trim()
        val image = _state.value.pickedImage
        if ((text.isBlank() && image == null) || _state.value.sending) return
        if (!socket.isConnected) {
            _state.value = _state.value.copy(error = appContext.getString(R.string.chat_offline))
            return
        }
        if (image != null) {
            sendWithImage(text, image)
        } else {
            sendText(text)
        }
    }

    private fun sendText(text: String) {
        _state.value = _state.value.copy(sending = true, error = null)
        val id = conversationId
        val ok = if (id != null) {
            socket.sendMessage(conversationId = id, recipientId = null, content = text)
        } else {
            val r = recipient
            if (r == null) false
            else socket.sendMessage(conversationId = null, recipientId = r.friendId, content = text)
        }
        if (!ok) {
            _state.value = _state.value.copy(sending = false, error = appContext.getString(R.string.chat_send_failed))
            return
        }
        // Otimista: aparece na hora; o reload a seguir reconcilia com o id real.
        val pending = PendingSend(content = text, senderId = myUserId)
        _state.value = _state.value.copy(
            input = "",
            sending = false,
            messages = _state.value.messages + ChatMessageDto(
                id = "pending:${pending.tempId}",
                conversationId = conversationId.orEmpty(),
                senderId = myUserId.orEmpty(),
                content = text,
                sentOnUtc = pending.sentOnUtc
            ),
            pending = _state.value.pending + pending
        )
        conversationId?.let { loadHistory(it) }
        if (conversationId == null && recipient != null) {
            resolveNewConversation(recipient.friendId)
        }
    }

    private fun sendWithImage(caption: String, image: PickedChatImage) {
        _state.value = _state.value.copy(sending = true, uploadingImage = true, error = null)
        viewModelScope.launch {
            when (val up = chat.uploadImage(image.bytes, image.mimeType)) {
                is AuthResult.Success -> {
                    val imageUrl = up.value
                    val content = if (caption.isBlank()) imageUrl else "$caption\n$imageUrl"
                    val id = conversationId
                    val ok = if (id != null) {
                        socket.sendMessage(conversationId = id, recipientId = null, content = content)
                    } else {
                        val r = recipient
                        if (r == null) false
                        else socket.sendMessage(conversationId = null, recipientId = r.friendId, content = content)
                    }
                    if (!ok) {
                        _state.value = _state.value.copy(
                            sending = false, uploadingImage = false,
                            error = appContext.getString(R.string.chat_send_failed)
                        )
                        return@launch
                    }
                    val pending = PendingSend(content = content, senderId = myUserId)
                    _state.value = _state.value.copy(
                        input = "",
                        pickedImage = null,
                        sending = false,
                        uploadingImage = false,
                        messages = _state.value.messages + ChatMessageDto(
                            id = "pending:${pending.tempId}",
                            conversationId = conversationId.orEmpty(),
                            senderId = myUserId.orEmpty(),
                            content = content,
                            sentOnUtc = pending.sentOnUtc
                        ),
                        pending = _state.value.pending + pending
                    )
                    conversationId?.let { loadHistory(it) }
                    if (conversationId == null && recipient != null) {
                        resolveNewConversation(recipient.friendId)
                    }
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(sending = false, uploadingImage = false)
                    handleError(up)
                }
            }
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
                is AuthResult.Success -> {
                    val server = merge(
                        _state.value.messages.filterNot { it.id.startsWith("pending:") },
                        r.value
                    )
                    // Casa pendentes 1-a-1 com o servidor; o que não casou continua visível.
                    val pool = server.toMutableList()
                    val stillPending = mutableListOf<PendingSend>()
                    for (p in _state.value.pending) {
                        val i = pool.indexOfFirst { it.senderId == p.senderId && it.content == p.content }
                        if (i >= 0) pool.removeAt(i) else stillPending.add(p)
                    }
                    _state.value = _state.value.copy(
                        messages = server + stillPending.map {
                            ChatMessageDto(
                                id = "pending:${it.tempId}",
                                conversationId = conversationId.orEmpty(),
                                senderId = it.senderId.orEmpty(),
                                content = it.content,
                                sentOnUtc = it.sentOnUtc
                            )
                        },
                        pending = stillPending,
                        loadingHistory = false,
                        error = null
                    )
                }
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
                when (val found = findConversation(friendId)) {
                    null -> Unit
                    else -> {
                        conversationId = found
                        onConversationOpened(found)
                        loadHistory(found)
                        socket.markRead(found)
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun findConversation(friendId: String): String? {
        return when (val r = chat.getConversations()) {
            is AuthResult.Success ->
                r.value.firstOrNull { c ->
                    c.participants.any { it.userId == friendId }
                }?.conversationId
            is AuthResult.Error -> {
                handleError(r)
                null
            }
        }
    }

    private fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.Connected -> {
                _state.value = _state.value.copy(connected = true, connectionInfo = appContext.getString(R.string.chat_online))
                // Reconexão (offline -> online): busca o que chegou nesse meio tempo.
                if (everConnected) {
                    conversationId?.let { loadHistory(it) }
                }
                everConnected = true
            }
            is ChatEvent.Disconnected ->
                _state.value = _state.value.copy(
                    connected = false,
                    connectionInfo = appContext.getString(R.string.chat_reconnecting)
                )
            is ChatEvent.AuthExpired -> onAuthExpired()
            is ChatEvent.Message -> {
                if (event.message.conversationId == conversationId) {
                    _state.value = _state.value.copy(
                        messages = merge(_state.value.messages, listOf(event.message))
                    )
                    socket.markRead(event.message.conversationId)
                }
            }
            is ChatEvent.ReadOk -> Unit
            is ChatEvent.Notification -> Unit
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
            appContext: Context,
            onAuthExpired: () -> Unit,
            onConversationOpened: (String) -> Unit,
            tokenRefresher: (suspend () -> String?)? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ConversationViewModel(
                        chat, tokens, baseUrl, myUserId, conversationId,
                        recipient, appContext, onAuthExpired, onConversationOpened, tokenRefresher
                    ) as T
            }
    }
}
