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
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()

    /**
     * Id do usuário atual (claim `sub` do JWT), sem validar assinatura.
     * Serve só para marcar "meus posts" na UI; a API continua autorizando tudo.
     */
    val userId: String?
        get() {
            val payload = accessToken?.split(".")?.getOrNull(1) ?: return null
            return try {
                val padded = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=')
                val json = String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE))
                org.json.JSONObject(json).optString("sub").ifBlank { null }
            } catch (_: Exception) {
                null
            }
        }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
