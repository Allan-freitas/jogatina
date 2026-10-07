package br.com.jogatina.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.games.GameDto
import br.com.jogatina.data.games.GameStatus
import br.com.jogatina.data.games.GamesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val query: String = "",
    val genre: String = "",
    val platform: String = "",
    val games: List<GameDto> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val adding: Set<String> = emptySet(),
    /** Jogo que deu 409 (já na biblioteca) aguardando confirmação de atualização. */
    val conflict: GameDto? = null
)

class CatalogViewModel(
    private val games: GamesRepository,
    private val inLibraryIds: () -> Set<String>,
    private val onLibraryChanged: () -> Unit,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun coverUrl(relative: String?): String? = games.coverUrl(relative)

    fun isInLibrary(gameId: String): Boolean = inLibraryIds().contains(gameId)

    init {
        loadPage(1)
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            loadPage(1)
        }
    }

    fun onFilterChange(genre: String, platform: String) {
        _state.value = _state.value.copy(genre = genre, platform = platform)
        loadPage(1)
    }

    fun clearFilters() {
        _state.value = _state.value.copy(query = "", genre = "", platform = "")
        loadPage(1)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        loadPage(s.page + 1, append = true)
    }

    private fun loadPage(page: Int, append: Boolean = false) {
        val s = _state.value
        _state.value = s.copy(
            loading = !append,
            loadingMore = append,
            error = null
        )
        viewModelScope.launch {
            val current = _state.value
            when (val r = games.getGames(
                search = current.query.ifBlank { null },
                genre = current.genre.ifBlank { null },
                platform = current.platform.ifBlank { null },
                page = page
            )) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        games = if (append) _state.value.games + r.value else r.value,
                        page = page,
                        hasMore = r.value.size >= PAGE_SIZE,
                        loading = false,
                        loadingMore = false
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, loadingMore = false)
                    handleError(r)
                }
            }
        }
    }

    fun add(game: GameDto, status: GameStatus, isFavorite: Boolean) {
        if (_state.value.adding.contains(game.id)) return
        _state.value = _state.value.copy(adding = _state.value.adding + game.id, error = null)
        viewModelScope.launch {
            when (val r = games.addToLibrary(game.id, status, isFavorite)) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(adding = _state.value.adding - game.id)
                    onLibraryChanged()
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(adding = _state.value.adding - game.id)
                    if (r.statusCode == 409) {
                        _state.value = _state.value.copy(conflict = game)
                    } else {
                        handleError(r)
                    }
                }
            }
        }
    }

    /**
     * Jogo já estava na biblioteca: atualiza via DELETE + POST (a API não tem update).
     */
    fun confirmUpdate(game: GameDto, status: GameStatus, isFavorite: Boolean) {
        _state.value = _state.value.copy(conflict = null, adding = _state.value.adding + game.id)
        viewModelScope.launch {
            games.removeFromLibrary(game.id)
            when (val r = games.addToLibrary(game.id, status, isFavorite)) {
                is AuthResult.Success -> onLibraryChanged()
                is AuthResult.Error -> handleError(r)
            }
            _state.value = _state.value.copy(adding = _state.value.adding - game.id)
        }
    }

    fun dismissConflict() {
        _state.value = _state.value.copy(conflict = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun handleError(error: AuthResult.Error) {
        if (error.statusCode == 401) onAuthExpired()
        else _state.value = _state.value.copy(error = error.message)
    }

    companion object {
        const val PAGE_SIZE = 20

        fun factory(
            games: GamesRepository,
            inLibraryIds: () -> Set<String>,
            onLibraryChanged: () -> Unit,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogViewModel(games, inLibraryIds, onLibraryChanged, onAuthExpired) as T
            }
    }
}
