package br.com.jogatina.data.auth

import br.com.jogatina.data.api.ApiClient
import org.json.JSONObject

/**
 * Endpoints de auth da Web.Api (ver CleanArchitecture.slnx):
 * - POST {baseUrl}/auth/login    { email, password }
 * - POST {baseUrl}/auth/register { email, firstName, lastName, password } -> Guid (string JSON)
 * - POST {baseUrl}/auth/social   { provider, token, providerUserId? }
 * - POST {baseUrl}/auth/refresh-token { refreshToken } -> { accessToken, refreshToken } (rotaciona)
 *
 * baseUrl padrão = https://agfapp.com (API de produção).
 * Ajuste via construtor quando apontar para dev/staging (ex.: http://10.0.2.2:5000 no emulador).
 */
class AuthRepository(
    baseUrl: String = DEFAULT_BASE_URL
) {
    private val api = ApiClient(baseUrl)

    suspend fun login(email: String, password: String): AuthResult<AccessTokensResponse> {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
        return api.post("auth/login", body).map { raw ->
            val json = JSONObject(raw)
            AccessTokensResponse(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken")
            )
        }
    }

    suspend fun register(
        email: String,
        firstName: String,
        lastName: String,
        password: String
    ): AuthResult<String> {
        val body = JSONObject()
            .put("email", email)
            .put("firstName", firstName)
            .put("lastName", lastName)
            .put("password", password)
        return api.post("auth/register", body).map { raw ->
            // API retorna o Guid do usuário criado (pode vir com aspas).
            raw.trim().trim('"')
        }
    }

    /**
     * Login social via Google (Credential Manager -> ID token).
     * O backend aceita provider como string livre (Google, Steam, PSN, Xbox...),
     * então "google" passa pelo SocialLoginCommand sem mudar a API.
     * O [token] aqui é o Google ID token (JWT) obtido no app.
     */
    suspend fun socialLoginGoogle(idToken: String): AuthResult<AccessTokensResponse> =
        socialLogin(provider = "google", token = idToken)

    suspend fun socialLogin(provider: String, token: String): AuthResult<AccessTokensResponse> {
        val body = JSONObject()
            .put("provider", provider)
            .put("token", token)
        return api.post("auth/social", body).map { raw ->
            val json = JSONObject(raw)
            AccessTokensResponse(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken")
            )
        }
    }

    /**
     * Troca o refresh token por um par novo (a API rotaciona: o antigo é invalidado).
     * Quem chama deve salvar o resultado no [TokenStore].
     */
    suspend fun refresh(refreshToken: String): AuthResult<AccessTokensResponse> {
        val body = JSONObject().put("refreshToken", refreshToken)
        return api.post("auth/refresh-token", body).map { raw ->
            val json = JSONObject(raw)
            AccessTokensResponse(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken")
            )
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://agfapp.com"

        /**
         * Web Client ID do Google Cloud Console (OAuth 2.0 Client do tipo Web).
         * O backend usa esse client para validar o ID token.
         * Troque pelo valor real do projeto antes de publicar.
         */
        const val GOOGLE_SERVER_CLIENT_ID = "255695637830-na5rq49nnb2er3nfc24e8hmcgfjfupot.apps.googleusercontent.com"
    }
}
