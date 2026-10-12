package br.com.jogatina.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.games.GameStatus
import br.com.jogatina.data.games.GamesRepository
import br.com.jogatina.data.games.MyGameDto
import br.com.jogatina.data.social.FriendDto
import br.com.jogatina.data.social.FriendsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NewCover(val bytes: ByteArray, val mimeType: String)

data class LibraryUiState(    val games: List<MyGameDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val filter: GameStatus? = null,
    val removing: Set<String> = emptySet(),
    val updating: Set<String> = emptySet(),
    val friends: List<FriendDto> = emptyList(),
    val friendsLoading: Boolean = false,
    val inviting: Boolean = false
) {
    val visible: List<MyGameDto>
        get() = games
            .filter { filter == null || it.status == filter }
            .sortedWith(compareByDescending<MyGameDto> { it.isFavorite }.thenByDescending { it.addedOnUtc })
}

class LibraryViewModel(
    private val games: GamesRepository,
    private val friends: FriendsRepository,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun coverUrl(relative: String?): String? = games.coverUrl(relative)

    init {
        refresh(first = true)
        loadFriends()
    }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(loading = first, refreshing = !first, error = null)
        viewModelScope.launch {
            when (val r = games.getMyGames()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        games = r.value, loading = false, refreshing = false
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, refreshing = false)
                    handleError(r)
                }
            }
        }
    }

    fun setFilter(filter: GameStatus?) {
        _state.value = _state.value.copy(filter = filter)
    }

    /** Carrega os amigos para o seletor "Jogar com" do diálogo do jogo. */
    fun loadFriends() {
        if (_state.value.friendsLoading) return
        _state.value = _state.value.copy(friendsLoading = true)
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

    /** Notifica os amigos marcados que o usuário quer jogar o jogo. */
    fun inviteToPlay(gameId: String, friendIds: List<String>) {
        if (friendIds.isEmpty() || _state.value.inviting) return
        _state.value = _state.value.copy(inviting = true, error = null)
        viewModelScope.launch {
            when (val r = games.inviteToPlay(gameId, friendIds)) {
                is AuthResult.Success -> _state.value = _state.value.copy(inviting = false)
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(inviting = false)
                    handleError(r)
                }
            }
        }
    }

    fun remove(game: MyGameDto) {
        if (_state.value.removing.contains(game.gameId)) return
        _state.value = _state.value.copy(removing = _state.value.removing + game.gameId)
        viewModelScope.launch {
            when (val r = games.removeFromLibrary(game.gameId)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        games = _state.value.games.filterNot { it.gameId == game.gameId },
                        removing = _state.value.removing - game.gameId
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(removing = _state.value.removing - game.gameId)
                    handleError(r)
                }
            }
        }
    }

    /**
     * A API não tem update: trocar status/favorito = DELETE + POST.
     * Se houver capa nova, ela é enviada antes (vale para todo o catálogo).
     */
    fun changeStatus(game: MyGameDto, status: GameStatus, isFavorite: Boolean) {
        updateGame(game, status, isFavorite, cover = null)
    }

    fun updateGame(game: MyGameDto, status: GameStatus, isFavorite: Boolean, cover: NewCover?) {
        if (_state.value.updating.contains(game.gameId)) return
        _state.value = _state.value.copy(updating = _state.value.updating + game.gameId, error = null)
        viewModelScope.launch {
            if (cover != null) {
                when (val up = games.uploadCover(game.gameId, cover.bytes, cover.mimeType)) {
                    is AuthResult.Success -> Unit
                    is AuthResult.Error -> {
                        _state.value = _state.value.copy(updating = _state.value.updating - game.gameId)
                        handleError(up)
                        return@launch
                    }
                }
            }
            val del = games.removeFromLibrary(game.gameId)
            if (del is AuthResult.Error && del.statusCode != 404) {
                _state.value = _state.value.copy(updating = _state.value.updating - game.gameId)
                handleError(del)
                return@launch
            }
            when (val add = games.addToLibrary(game.gameId, status, isFavorite)) {
                is AuthResult.Success -> refresh()
                is AuthResult.Error -> handleError(add)
            }
            _state.value = _state.value.copy(updating = _state.value.updating - game.gameId)
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
            games: GamesRepository,
            friends: FriendsRepository,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(games, friends, onAuthExpired) as T
            }
    }
}
