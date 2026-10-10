package br.com.jogatina.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.TokenStore
import br.com.jogatina.data.chat.ChatRepository
import br.com.jogatina.data.chat.ConversationDto
import br.com.jogatina.data.social.FriendDto
import br.com.jogatina.data.social.FriendsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatListUiState(
    val conversations: List<ConversationDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val myUserId: String? = null,
    val showNewChat: Boolean = false,
    val friends: List<FriendDto> = emptyList(),
    val friendsLoading: Boolean = false
) {
    val unreadTotal: Int get() = conversations.sumOf { it.unreadCount }
}

class ChatListViewModel(
    private val chat: ChatRepository,
    private val friends: FriendsRepository,
    tokens: TokenStore,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState(myUserId = tokens.userId))
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init {
        refresh(first = true)
    }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(loading = first, refreshing = !first, error = null)
        viewModelScope.launch {
            when (val r = chat.getConversations()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        conversations = r.value, loading = false, refreshing = false
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, refreshing = false)
                    handleError(r)
                }
            }
        }
    }

    fun openNewChat() {
        _state.value = _state.value.copy(showNewChat = true, friendsLoading = true)
        viewModelScope.launch {
            when (val r = friends.getFriends()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(friends = r.value, friendsLoading = false)
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(friendsLoading = false)
                    handleError(r)
                }
            }
        }
    }

    fun closeNewChat() {
        _state.value = _state.value.copy(showNewChat = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun handleError(error: AuthResult.Error) {
        if (error.statusCode == 401) onAuthExpired()
        else _state.value = _state.value.copy(error = error.message)
    }

    companion object {
        fun factory(
            chat: ChatRepository,
            friends: FriendsRepository,
            tokens: TokenStore,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatListViewModel(chat, friends, tokens, onAuthExpired) as T
            }
    }
}
