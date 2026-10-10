package br.com.jogatina.data.feed

/**
 * Contratos espelhando os endpoints `feed/...` da Web.Api:
 * - GET    feed/posts?{page,pageSize} -> [PostDto]
 * - POST   feed/posts { content?, imageUrl? } -> Guid
 * - POST   feed/images (multipart file) -> { imageUrl }
 * - POST   feed/posts/{id}/likes -> { liked, likeCount }
 * - GET    feed/posts/{id}/comments -> [CommentDto] (com Replies)
 * - POST   feed/posts/{id}/comments { content, parentCommentId? } -> Guid
 * - DELETE feed/posts/{id} -> Guid
 *
 * imageUrl é relativo (ex.: "/uploads/abc.jpg"); complete com a base da API.
 */

data class PostDto(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String?,
    val imageUrl: String?,
    val createdOnUtc: String,
    val totalReactions: Int,
    val reactionCounts: Map<String, Int>,
    val myReaction: String?,
    val commentCount: Int
)

data class CommentDto(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val content: String,
    val createdOnUtc: String,
    val replies: List<CommentDto> = emptyList()
)

data class LikeResult(val liked: Boolean, val likeCount: Int)

/** Reactions disponíveis (kinds aceitos pela API). */
enum class Reaction(val kind: String, val emoji: String, val labelRes: Int) {
    HEART("heart", "❤️", br.com.jogatina.R.string.reaction_heart),
    CELEBRATE("celebrate", "🎉", br.com.jogatina.R.string.reaction_celebrate),
    WOW("wow", "😮", br.com.jogatina.R.string.reaction_wow),
    HAHA("haha", "😂", br.com.jogatina.R.string.reaction_haha),
    INSIGHTFUL("insightful", "💡", br.com.jogatina.R.string.reaction_insightful);

    companion object {
        fun fromKind(kind: String?): Reaction? =
            entries.firstOrNull { it.kind.equals(kind, ignoreCase = true) }

        /** Emojis dos top kinds para o resumo (ex.: ❤️😮 12). */
        fun topEmojis(counts: Map<String, Int>, take: Int = 3): String =
            counts.entries
                .sortedByDescending { it.value }
                .take(take)
                .mapNotNull { fromKind(it.key)?.emoji }
                .joinToString("")
    }
}

data class ReactionResult(
    val myReaction: String?,
    val total: Int,
    val counts: Map<String, Int>
)
