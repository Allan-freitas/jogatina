package br.com.jogatina.data.chat

/**
 * Espelha o Chat da Web.Api:
 * - GET chat/conversations -> [ConversationDto]
 * - GET chat/conversations/{id}/messages?page=&pageSize= -> [ChatMessageDto] (cronológica)
 * - WS  /ws/chat?access_token=... (frames JSON: send/read/ping <-> message/read_ok/pong/error)
 */

data class ChatParticipant(
    val userId: String,
    val firstName: String,
    val lastName: String
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class ConversationLastMessage(
    val id: String,
    val senderId: String,
    val content: String,
    val sentOnUtc: String
)

data class ConversationDto(
    val conversationId: String,
    val participants: List<ChatParticipant>,
    val lastMessage: ConversationLastMessage?,
    val unreadCount: Int,
    val updatedOnUtc: String
) {
    /** Nome para exibir: outro participante (ou grupo). */
    fun title(myUserId: String?): String {
        val others = participants.filter { it.userId != myUserId }
        return when {
            others.isEmpty() -> participants.firstOrNull()?.fullName ?: "Conversa"
            others.size == 1 -> others.first().fullName.ifBlank { "Conversa" }
            else -> others.joinToString(", ") { it.firstName }.ifBlank { "Grupo" }
        }
    }
}

data class ChatMessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val sentOnUtc: String
)
