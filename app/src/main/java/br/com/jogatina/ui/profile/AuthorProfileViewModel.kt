package br.com.jogatina.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.games.GamesRepository
import br.com.jogatina.data.games.MyGameDto
import br.com.jogatina.data.users.PublicProfile
import br.com.jogatina.data.users.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthorProfileUiState(
    val profile: PublicProfile? = null,
    val games: List<MyGameDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class AuthorProfileViewModel(
    private val users: UserRepository,
    private val games: GamesRepository,
    private val authorId: String,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(AuthorProfileUiState())
    val state: StateFlow<AuthorProfileUiState> = _state.asStateFlow()

    fun coverUrl(relative: String?): String? = games.coverUrl(relative)

    fun photoUrl(relative: String?): String? = users.photoUrl(relative)

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.value = AuthorProfileUiState(loading = true)
        viewModelScope.launch {
            when (val p = users.getPublicProfile(authorId)) {
                is AuthResult.Error -> {
                    _state.value = AuthorProfileUiState(loading = false)
                    handleError(p)
                    return@launch
                }
                is AuthResult.Success -> {
                    val profile = p.value
                    when (val g = games.getUserGames(authorId)) {
                        is AuthResult.Success ->
                            _state.value = AuthorProfileUiState(profile = profile, games = g.value)
                        is AuthResult.Error -> {
                            // Perfil vale mesmo se os jogos falharem.
                            _state.value = AuthorProfileUiState(profile = profile)
                            handleError(g)
                        }
                    }
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
            users: UserRepository,
            games: GamesRepository,
            authorId: String,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthorProfileViewModel(users, games, authorId, onAuthExpired) as T
            }
    }
}
