package br.com.jogatina.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.social.FriendDto
import br.com.jogatina.data.social.FriendsRepository
import br.com.jogatina.data.social.PendingRequest
import br.com.jogatina.data.social.SearchedUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FriendsTab { FRIENDS, REQUESTS, SEARCH }

data class FriendsUiState(
    val tab: FriendsTab = FriendsTab.FRIENDS,
    val friends: List<FriendDto> = emptyList(),
    val requests: List<PendingRequest> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchedUser> = emptyList(),
    val searching: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val busyIds: Set<String> = emptySet()
) {
    val incomingCount: Int get() = requests.count { it.incoming }

    fun relationOf(userId: String): Relation =
        when {
            friends.any { it.friendId == userId } -> Relation.FRIEND
            requests.any { !it.incoming && it.otherUserId == userId } -> Relation.SENT
            else -> Relation.NONE
        }
}

enum class Relation { FRIEND, SENT, NONE }

class FriendsViewModel(
    private val social: FriendsRepository,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh(first = true)
    }

    fun setTab(tab: FriendsTab) {
        _state.value = _state.value.copy(tab = tab)
    }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(loading = first, refreshing = !first, error = null)
        viewModelScope.launch {
            val friendsResult = social.getFriends()
            val requestsResult = social.getPendingRequests()
            val error = (friendsResult as? AuthResult.Error)
                ?: (requestsResult as? AuthResult.Error)
            if (error != null) {
                _state.value = _state.value.copy(loading = false, refreshing = false)
                handleError(error)
                return@launch
            }
            _state.value = _state.value.copy(
                friends = (friendsResult as AuthResult.Success).value,
                requests = (requestsResult as AuthResult.Success).value,
                loading = false,
                refreshing = false
            )
        }
    }

    fun onSearchChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.value = _state.value.copy(searchResults = emptyList(), searching = false)
            return
        }
        _state.value = _state.value.copy(searching = true)
        searchJob = viewModelScope.launch {
            delay(500)
            when (val r = social.searchUsers(query.trim())) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(searchResults = r.value, searching = false)
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(searching = false)
                    handleError(r)
                }
            }
        }
    }

    fun sendRequest(userId: String) {
        if (_state.value.busyIds.contains(userId)) return
        _state.value = _state.value.copy(busyIds = _state.value.busyIds + userId)
        viewModelScope.launch {
            when (val r = social.sendRequest(userId)) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(busyIds = _state.value.busyIds - userId)
                    refresh()
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(busyIds = _state.value.busyIds - userId)
                    handleError(r)
                }
            }
        }
    }

    fun respond(request: PendingRequest, accept: Boolean) {
        if (_state.value.busyIds.contains(request.requestId)) return
        _state.value = _state.value.copy(busyIds = _state.value.busyIds + request.requestId)
        viewModelScope.launch {
            when (val r = social.respondRequest(request.requestId, accept)) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(busyIds = _state.value.busyIds - request.requestId)
                    refresh()
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(busyIds = _state.value.busyIds - request.requestId)
                    handleError(r)
                }
            }
        }
    }

    fun removeFriend(friend: FriendDto) {
        if (_state.value.busyIds.contains(friend.friendId)) return
        _state.value = _state.value.copy(busyIds = _state.value.busyIds + friend.friendId)
        viewModelScope.launch {
            when (val r = social.removeFriend(friend.friendId)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        friends = _state.value.friends.filterNot { it.friendId == friend.friendId },
                        busyIds = _state.value.busyIds - friend.friendId
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(busyIds = _state.value.busyIds - friend.friendId)
                    handleError(r)
                }
            }
        }
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
            social: FriendsRepository,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FriendsViewModel(social, onAuthExpired) as T
            }
    }
}
