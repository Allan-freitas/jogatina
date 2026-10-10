package br.com.jogatina.data.api

import android.util.Log
import br.com.jogatina.data.auth.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP mínimo (sem Retrofit) para a Web.Api em https://agfapp.com.
 * Suporta GET/POST/DELETE JSON + upload multipart, com Bearer opcional.
 */
class ApiClient(val baseUrl: String) {

    suspend fun get(path: String, token: String? = null): AuthResult<String> =
        withContext(Dispatchers.IO) { request("GET", path, null, token) }

    suspend fun post(path: String, body: JSONObject, token: String? = null): AuthResult<String> =
        withContext(Dispatchers.IO) { request("POST", path, body.toString().toByteArray(), token) }

    suspend fun postEmpty(path: String, token: String? = null): AuthResult<String> =
        withContext(Dispatchers.IO) { request("POST", path, null, token) }

    suspend fun patchEmpty(path: String, token: String? = null): AuthResult<String> =
        withContext(Dispatchers.IO) { request("PATCH", path, null, token) }

    suspend fun delete(path: String, token: String? = null): AuthResult<String> =
        withContext(Dispatchers.IO) { request("DELETE", path, null, token) }

    suspend fun upload(
        path: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        token: String? = null,
        method: String = "POST"
    ): AuthResult<String> = withContext(Dispatchers.IO) {
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
            readResult(conn, "UPLOAD $method", path, startedAt)
        } catch (e: Exception) {
            Log.e(TAG, "UPLOAD $method $path falhou: ${e.javaClass.simpleName}: ${e.message}")
            AuthResult.Error(e.message ?: "Falha de rede", null)
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
            readTimeout = 15_000
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
