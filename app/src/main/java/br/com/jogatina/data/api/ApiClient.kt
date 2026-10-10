package br.com.jogatina.data.api

import android.util.Log
import br.com.jogatina.data.auth.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP mínimo (sem Retrofit) para a Web.Api em https://agfapp.com.
 * Suporta GET/POST/PUT/PATCH/DELETE JSON + upload multipart, com Bearer opcional.
 *
 * Resiliência a cold start (a API "dorme" sem tráfego): leitura com timeout
 * de 30 s + UMA nova tentativa automática em falha de rede (timeout, DNS,
 * conexão recusada). Erros HTTP (4xx/5xx) não repetem.
 *
 * Renovação de sessão: quando [tokenProvider]/[tokenRefresher] estão configurados
 * (ver MainActivity), um 401 com token anexado tenta UM refresh e repete a
 * chamada uma vez com o novo access token. Falhas de refresh mantêm o 401
 * original, e o fluxo existente de logout (onAuthExpired) continua valendo.
 */
class ApiClient(
    val baseUrl: String,
    var tokenProvider: (() -> String?)? = null,
    var tokenRefresher: (suspend () -> String?)? = null
) {
    private val refreshMutex = Mutex()

    suspend fun get(path: String, token: String? = null): AuthResult<String> =
        withAuth(token) { t -> withRetry { request("GET", path, null, t) } }

    suspend fun post(path: String, body: JSONObject, token: String? = null): AuthResult<String> =
        withAuth(token) { t -> withRetry { request("POST", path, body.toString().toByteArray(), t) } }

    suspend fun put(path: String, body: JSONObject, token: String? = null): AuthResult<String> =
        withAuth(token) { t -> withRetry { request("PUT", path, body.toString().toByteArray(), t) } }

    suspend fun postEmpty(path: String, token: String? = null): AuthResult<String> =
        withAuth(token) { t -> withRetry { request("POST", path, null, t) } }

    suspend fun patchEmpty(path: String, token: String? = null): AuthResult<String> =
        withAuth(token) { t -> withRetry { request("PATCH", path, null, t) } }

    suspend fun delete(path: String, token: String? = null): AuthResult<String> =
        withAuth(token) { t -> withRetry { request("DELETE", path, null, t) } }

    suspend fun upload(
        path: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        token: String? = null,
        method: String = "POST"
    ): AuthResult<String> = withAuth(token) { t ->
        withRetry { uploadOnce(path, bytes, fileName, mimeType, t, method) }
    }

    /**
     * Executa a chamada; em 401 com Bearer anexado, tenta renovar a sessão
     * uma vez e repete a chamada com o novo token.
     */
    private suspend fun withAuth(
        token: String?,
        call: suspend (String?) -> AuthResult<String>
    ): AuthResult<String> {
        val first = call(token)
        if (first is AuthResult.Error && first.statusCode == 401 && !token.isNullOrBlank()) {
            val fresh = refreshAccessToken(token)
            if (!fresh.isNullOrBlank() && fresh != token) {
                return call(fresh)
            }
        }
        return first
    }

    /**
     * Renova o access token via [tokenRefresher], com Mutex para que N
     * chamadas 401 simultâneas disparem UM único refresh. Se outra coroutine
     * já renovou enquanto esta esperava, reaproveita o token atual.
     */
    private suspend fun refreshAccessToken(failedToken: String): String? {
        val refresher = tokenRefresher ?: return null
        return refreshMutex.withLock {
            val current = try {
                tokenProvider?.invoke()
            } catch (_: Exception) {
                null
            }
            if (!current.isNullOrBlank() && current != failedToken) return current
            try {
                refresher()
            } catch (e: Exception) {
                Log.e(TAG, "refresh falhou: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    /**
     * Executa em IO com uma nova tentativa em caso de falha de rede.
     * A primeira tentativa geralmente "acorda" a API; a segunda passa.
     */
    private suspend fun withRetry(block: () -> AuthResult<String>): AuthResult<String> =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: IOException) {
                Log.d(TAG, "falha de rede (${e.javaClass.simpleName}), nova tentativa em 1s")
                delay(1_000)
                try {
                    block()
                } catch (e2: Exception) {
                    Log.e(TAG, "retry falhou: ${e2.javaClass.simpleName}: ${e2.message}")
                    AuthResult.Error(e2.message ?: "Falha de rede", null)
                }
            }
        }

    private fun uploadOnce(
        path: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        token: String?,
        method: String
    ): AuthResult<String> {
        val startedAt = System.currentTimeMillis()
        val boundary = "jogatina${System.currentTimeMillis()}"
        val url = URL("${baseUrl.trimEnd('/')}/$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { out ->
                val writer = out.bufferedWriter(Charsets.UTF_8)
                writer.append("--").append(boundary).append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(fileName).append("\"\r\n")
                writer.append("Content-Type: ").append(mimeType).append("\r\n\r\n")
                writer.flush()
                out.write(bytes)
                out.flush()
                writer.append("\r\n--").append(boundary).append("--\r\n")
                writer.flush()
            }
            return readResult(conn, "UPLOAD $method", path, startedAt)
        } catch (e: IOException) {
            Log.e(TAG, "UPLOAD $method $path falhou: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "UPLOAD $method $path falhou: ${e.javaClass.simpleName}: ${e.message}")
            return AuthResult.Error(e.message ?: "Falha de rede", null)
        } finally {
            conn.disconnect()
        }
    }

    private fun request(
        method: String,
        path: String,
        body: ByteArray?,
        token: String?
    ): AuthResult<String> {
        val startedAt = System.currentTimeMillis()
        val url = URL("${baseUrl.trimEnd('/')}/$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body) }
            readResult(conn, method, path, startedAt)
        } catch (e: IOException) {
            Log.e(TAG, "$method $path falhou: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$method $path falhou: ${e.javaClass.simpleName}: ${e.message}")
            AuthResult.Error(e.message ?: "Falha de rede", null)
        } finally {
            conn.disconnect()
        }
    }

    private fun readResult(conn: HttpURLConnection, method: String, path: String, startedAt: Long): AuthResult<String> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        Log.d(TAG, "$method $path -> $code (${System.currentTimeMillis() - startedAt}ms)")
        return if (code in 200..299) {
            AuthResult.Success(raw)
        } else {
            AuthResult.Error(parseProblem(raw, code), code)
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

    companion object {
        private const val TAG = "JogatinaApi"
    }
}
