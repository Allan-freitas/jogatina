package br.com.jogatina.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthRepository
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WelcomeUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val showEmailForm: Boolean = false,
    val emailMode: EmailMode = EmailMode.LOGIN
)

enum class EmailMode { LOGIN, REGISTER }

class WelcomeViewModel(
    private val auth: AuthRepository,
    private val tokens: TokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeUiState(loggedIn = tokens.isLoggedIn))
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    fun showEmailForm(mode: EmailMode) {
        _state.value = _state.value.copy(showEmailForm = true, emailMode = mode, error = null)
    }

    fun dismissEmailForm() {
        _state.value = _state.value.copy(showEmailForm = false, error = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Botão principal "Entrar" (Discord). Sem OAuth nativo ainda: abre o form de e-mail. */
    fun onDiscordEnter() {
        // Quando o OAuth Discord estiver plugado, chame socialLogin(token) aqui.
        showEmailForm(EmailMode.LOGIN)
    }

    fun socialLogin(discordToken: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = auth.socialLoginDiscord(discordToken)) {
                is AuthResult.Success -> {
                    tokens.save(r.value)
                    _state.value = _state.value.copy(loading = false, loggedIn = true)
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, error = r.message)
                }
            }
        }
    }

    fun login(email: String, password: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = auth.login(email, password)) {
                is AuthResult.Success -> {
                    tokens.save(r.value)
                    _state.value = _state.value.copy(loading = false, loggedIn = true)
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, error = r.message)
                }
            }
        }
    }

    fun register(email: String, firstName: String, lastName: String, password: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = auth.register(email, firstName, lastName, password)) {
                is AuthResult.Success -> {
                    // Registro retorna só o Id; faz login em seguida para obter tokens.
                    when (val l = auth.login(email, password)) {
                        is AuthResult.Success -> {
                            tokens.save(l.value)
                            _state.value = _state.value.copy(loading = false, loggedIn = true)
                        }
                        is AuthResult.Error -> {
                            _state.value = _state.value.copy(
                                loading = false,
                                error = "Conta criada! Faça login para continuar."
                            )
                        }
                    }
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, error = r.message)
                }
            }
        }
    }

    fun logout() {
        tokens.clear()
        _state.value = WelcomeUiState(loggedIn = false)
    }

    companion object {
        fun factory(auth: AuthRepository, tokens: TokenStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WelcomeViewModel(auth, tokens) as T
            }
    }
}
