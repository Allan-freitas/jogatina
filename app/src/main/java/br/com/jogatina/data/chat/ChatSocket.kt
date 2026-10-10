package br.com.jogatina.data.chat

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Eventos vindos do WS /ws/chat. */
sealed interface ChatEvent {
    data class Message(val message: ChatMessageDto) : ChatEvent
    data class ReadOk(val conversationId: String) : ChatEvent
    data class Error(val code: String, val message: String) : ChatEvent
    data object Connected : ChatEvent
    data class Disconnected(val reason: String) : ChatEvent
    /** Handshake rejeitado por token inválido/expirado, mesmo após tentar renovar. */
    data object AuthExpired : ChatEvent
}

/**
 * WebSocket do chat (OkHttp). Autentica via ?access_token=.
 * Um socket por usuário; mensagens de qualquer conversa chegam nele.
 *
 * O token é lido de [tokenProvider] a cada (re)conexão, então uma renovação
 * do access token via REST é adotada automaticamente no próximo handshake.
 * Se o handshake falhar com 401, tenta UMA renovação via [tokenRefresher] e
 * reconecta; se continuar 401 (ou sem refresh possível), emite
 * [ChatEvent.AuthExpired] e para de tentar — espelhando o logout do REST.
 */
class ChatSocket(
    private val baseUrl: String,
    private val scope: CoroutineScope,
    private val tokenProvider: () -> String?,
    private val tokenRefresher: (suspend () -> String?)? = null
) {
    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private var wantConnection = false
    /** Token usado no handshake em curso/mais recente (para detectar 401 repetido). */
    private var activeToken: String? = null
    private var lastAuthRetryToken: String? = null

    val isConnected: Boolean get() = socket != null

    fun connect() {
        wantConnection = true
        lastAuthRetryToken = null
        open()
    }

    fun disconnect() {
        wantConnection = false
        pingJob?.cancel()
        pingJob = null
        try {
            socket?.close(1000, "bye")
        } catch (_: Exception) {
        }
        socket = null
    }

    fun sendMessage(conversationId: String?, recipientId: String?, content: String): Boolean {
        val frame = JSONObject().put("type", "send").put("content", content)
        if (conversationId != null) frame.put("conversationId", conversationId)
        if (recipientId != null) frame.put("recipientId", recipientId)
        return send(frame.toString())
    }

    fun markRead(conversationId: String): Boolean {
        val frame = JSONObject()
            .put("type", "read")
            .put("conversationId", conversationId)
        return send(frame.toString())
    }

    private fun send(text: String): Boolean {
        val s = socket ?: return false
        return try {
            s.send(text)
        } catch (e: Exception) {
            Log.e(TAG, "send falhou: ${e.message}")
            false
        }
    }

    private fun open() {
        val t = tokenProvider() ?: return
        activeToken = t
        disconnectSocketOnly()
        val url = baseUrl.trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws/chat?access_token=" + t
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, Listener())
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(PING_MS)
                val s = socket
                if (s == null) {
                    if (wantConnection) open()
                    return@launch
                }
                try {
                    s.send(JSONObject().put("type", "ping").toString())
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun disconnectSocketOnly() {
        try {
            socket?.cancel()
        } catch (_: Exception) {
        }
        socket = null
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            lastAuthRetryToken = null
            scope.launch { _events.emit(ChatEvent.Connected) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "message" -> ChatRepository.parseMessagePayload(json)?.let {
                            _events.emit(ChatEvent.Message(it))
                        }
                        "read_ok" -> _events.emit(
                            ChatEvent.ReadOk(json.optString("conversationId")))
                        "pong" -> Unit
                        "error" -> _events.emit(ChatEvent.Error(
                            json.optString("code"), json.optString("message")))
                        else -> Log.d(TAG, "frame ignorado: $text")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "frame inválido: ${e.message}")
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "ws failure: ${t.message} (http=${response?.code})")
            socket = null
            if (response?.code == 401 && wantConnection) {
                onHandshakeUnauthorized()
            } else {
                scope.launch { _events.emit(ChatEvent.Disconnected(t.message ?: "falha")) }
            }
        }

        /**
         * Handshake rejeitado: tenta renovar a sessão uma vez e reconecta com o
         * token fresco. Se o token que falhou já era fruto de uma tentativa de
         * renovação (ou não há como renovar), desiste e avisa expiração — o
         * ViewModel então desloga, como no REST.
         */
        private fun onHandshakeUnauthorized() {
            val failed = activeToken
            if (failed != null && failed == lastAuthRetryToken) {
                wantConnection = false
                scope.launch { _events.emit(ChatEvent.AuthExpired) }
                return
            }
            lastAuthRetryToken = failed
            scope.launch {
                val fresh = try {
                    tokenRefresher?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "ws refresh falhou: ${e.message}")
                    null
                }
                if (!fresh.isNullOrBlank() && wantConnection) {
                    open()
                } else if (wantConnection) {
                    wantConnection = false
                    _events.emit(ChatEvent.AuthExpired)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            scope.launch { _events.emit(ChatEvent.Disconnected(reason.ifBlank { "fechado" })) }
        }
    }

    companion object {
        private const val TAG = "JogatinaChat"
        private const val PING_MS = 25_000L

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                // Cold start: handshake pode demorar; leitura sem timeout
                // (WS é longa duração, com ping de app a cada 25 s).
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.MINUTES)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
}
