package br.com.jogatina.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Guarda access/refresh tokens após login/registro.
 * Troque por DataStore criptografado quando for para produção.
 */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jogatina_auth", Context.MODE_PRIVATE)

    fun save(tokens: AccessTokensResponse) {
        prefs.edit {
            putString(KEY_ACCESS, tokens.accessToken)
            putString(KEY_REFRESH, tokens.refreshToken)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_ACCESS)
            remove(KEY_REFRESH)
        }
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
