package br.com.jogatina.data.auth

import br.com.jogatina.data.api.ApiClient
import org.json.JSONObject

/**
 * Endpoints de auth da Web.Api (ver CleanArchitecture.slnx):
 * - POST {baseUrl}/auth/login    { email, password }
 * - POST {baseUrl}/auth/register { email, firstName, lastName, password } -> Guid (string JSON)
 * - POST {baseUrl}/auth/social   { provider, token, providerUserId? }
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
     * Botão "Entrar" da Welcome usa provider "discord".
     * O backend aceita provider como string livre (Google, Steam, PSN, Xbox...),
     * então "discord" passa pelo SocialLoginCommand sem mudar a API.
     */
    suspend fun socialLoginDiscord(discordToken: String): AuthResult<AccessTokensResponse> {
        val body = JSONObject()
            .put("provider", "discord")
            .put("token", discordToken)
        return api.post("auth/social", body).map { raw ->
            val json = JSONObject(raw)
            AccessTokensResponse(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken")
            )
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://agfapp.com"
    }
}
