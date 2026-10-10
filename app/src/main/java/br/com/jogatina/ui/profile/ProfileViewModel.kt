package br.com.jogatina.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.users.UserProfile
import br.com.jogatina.data.users.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PickedPhoto(val bytes: ByteArray, val mimeType: String, val previewUri: Uri)

data class ProfileUiState(
    val profile: UserProfile? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
    val lastSavedAt: Long = 0L
)

class ProfileViewModel(
    private val users: UserRepository,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun photoUrl(relative: String?): String? = users.photoUrl(relative)

    init {
        refresh(first = true)
    }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(loading = first, refreshing = !first, error = null)
        viewModelScope.launch {
            when (val r = users.getMyProfile()) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(profile = r.value, loading = false, refreshing = false)
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, refreshing = false)
                    handleError(r)
                }
            }
        }
    }

    /**
     * Salva foto (se trocada) + nascimento/hobbies. Null = manter atual.
     */
    fun save(birthDateIso: String?, hobbies: String?, photo: PickedPhoto?) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            if (photo != null) {
                when (val up = users.uploadPhoto(photo.bytes, photo.mimeType)) {
                    is AuthResult.Success -> Unit
                    is AuthResult.Error -> {
                        _state.value = _state.value.copy(saving = false)
                        handleError(up)
                        return@launch
                    }
                }
            }
            when (val r = users.updateProfile(birthDateIso, hobbies)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        profile = r.value,
                        saving = false,
                        lastSavedAt = System.currentTimeMillis()
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(saving = false)
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
        fun factory(users: UserRepository, onAuthExpired: () -> Unit): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProfileViewModel(users, onAuthExpired) as T
            }
    }
}
