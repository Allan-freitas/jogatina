package br.com.jogatina.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP mínimo (sem Retrofit) para os endpoints de auth da Web.Api.
 *
 * Endpoints (ver CleanArchitecture.slnx):
 * - POST {baseUrl}/auth/login    { email, password }
 * - POST {baseUrl}/auth/register { email, firstName, lastName, password } -> Guid (string JSON)
 * - POST {baseUrl}/auth/social   { provider, token, providerUserId? }
 *
 * baseUrl padrão = https://agfapp.com (API de produção).
 * Ajuste via construtor quando apontar para dev/staging (ex.: http://10.0.2.2:5000 no emulador).
 */
class AuthRepository(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    suspend fun login(email: String, password: String): AuthResult<AccessTokensResponse> =
        postForTokens(
            path = "auth/login",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
        )

    suspend fun register(
        email: String,
        firstName: String,
        lastName: String,
        password: String
    ): AuthResult<String> = withContext(Dispatchers.IO) {
        post(
            path = "auth/register",
            body = JSONObject()
                .put("email", email)
                .put("firstName", firstName)
                .put("lastName", lastName)
                .put("password", password)
        ).map { raw ->
            // API retorna o Guid do usuário criado (pode vir com aspas).
            raw.trim().trim('"')
        }
    }

    /**
     * Botão "Entrar" da Welcome usa provider "discord".
     * O backend aceita provider como string livre (Google, Steam, PSN, Xbox...),
     * então "discord" passa pelo SocialLoginCommand sem mudar a API.
     */
    suspend fun socialLoginDiscord(discordToken: String): AuthResult<AccessTokensResponse> =
        postForTokens(
            path = "auth/social",
            body = JSONObject()
                .put("provider", "discord")
                .put("token", discordToken)
        )

    private suspend fun postForTokens(
        path: String,
        body: JSONObject
    ): AuthResult<AccessTokensResponse> = withContext(Dispatchers.IO) {
        post(path, body).map { raw ->
            val json = JSONObject(raw)
            AccessTokensResponse(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken")
            )
        }
    }

    private fun post(path: String, body: JSONObject): AuthResult<String> {
        val url = URL("${baseUrl.trimEnd('/')}/$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                AuthResult.Success(raw)
            } else {
                AuthResult.Error(parseProblem(raw, code), code)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Falha de rede", null)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseProblem(raw: String, code: Int): String {
        if (raw.isBlank()) return "Erro $code"
        return try {
            val json = JSONObject(raw)
            json.optString("detail", json.optString("title", raw))
        } catch (_: Exception) {
            raw
        }
    }

    private inline fun <T, R> AuthResult<T>.map(transform: (T) -> R): AuthResult<R> =
        when (this) {
            is AuthResult.Success -> try {
                AuthResult.Success(transform(value))
            } catch (e: Exception) {
                AuthResult.Error("Resposta inválida da API: ${e.message}")
            }
            is AuthResult.Error -> this
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://agfapp.com"
    }
}
