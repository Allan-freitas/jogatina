package br.com.jogatina.data.chat

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONArray
import org.json.JSONObject

class ChatRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    suspend fun getConversations(): AuthResult<List<ConversationDto>> =
        api.get("chat/conversations", token()).map { raw -> parseConversations(JSONArray(raw)) }

    suspend fun getMessages(conversationId: String, page: Int = 1, pageSize: Int = 50): AuthResult<List<ChatMessageDto>> =
        api.get("chat/conversations/$conversationId/messages?page=$page&pageSize=$pageSize", token())
            .map { raw -> parseMessages(JSONArray(raw)) }

    fun imageUrl(relative: String?): String? =
        relative?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let {
                if (it.startsWith("http://") || it.startsWith("https://")) it
                else api.baseUrl.trimEnd('/') + it
            }

    /**
     * Hospeda imagem do chat (teclado GIF/figurinha ou galeria).
     * `POST chat/images` multipart `file` (JPEG/PNG/WebP/GIF ≤ 5 MB) -> `{ "imageUrl": "/chat-images/..." }`.
     * A URL relativa retornada é enviada como [content] via WS `send`.
     */
    suspend fun uploadImage(bytes: ByteArray, mimeType: String): AuthResult<String> {
        val ext = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        return api.upload("chat/images", bytes, "upload.$ext", mimeType, token()).map { raw ->
            JSONObject(raw).getString("imageUrl")
        }
    }

    private fun parseConversations(array: JSONArray): List<ConversationDto> =
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val parts = o.optJSONArray("participants")
            ConversationDto(
                conversationId = o.getString("conversationId"),
                participants = if (parts != null) {
                    List(parts.length()) { j ->
                        val p = parts.getJSONObject(j)
                        ChatParticipant(
                            userId = p.getString("userId"),
                            firstName = p.optString("firstName"),
                            lastName = p.optString("lastName")
                        )
                    }
                } else emptyList(),
                lastMessage = o.optJSONObject("lastMessage")?.let { m ->
                    ConversationLastMessage(
                        id = m.getString("id"),
                        senderId = m.getString("senderId"),
                        content = m.optString("content"),
                        sentOnUtc = m.optString("sentOnUtc")
                    )
                },
                unreadCount = o.optInt("unreadCount"),
                updatedOnUtc = o.optString("updatedOnUtc")
            )
        }

    private fun parseMessages(array: JSONArray): List<ChatMessageDto> =
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            ChatMessageDto(
                id = o.getString("id"),
                conversationId = o.getString("conversationId"),
                senderId = o.getString("senderId"),
                content = o.getString("content"),
                sentOnUtc = o.optString("sentOnUtc")
            )
        }

    companion object {
        fun parseMessagePayload(json: JSONObject): ChatMessageDto? = try {
            ChatMessageDto(
                id = json.getString("id"),
                conversationId = json.getString("conversationId"),
                senderId = json.getString("senderId"),
                content = json.getString("content"),
                sentOnUtc = json.optString("sentOnUtc")
            )
        } catch (_: Exception) {
            null
        }
    }
}
