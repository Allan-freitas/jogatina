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
    val content: String?,
    val imageUrl: String?,
    val createdOnUtc: String,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean
)

data class CommentDto(
    val id: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdOnUtc: String,
    val replies: List<CommentDto> = emptyList()
)

data class LikeResult(val liked: Boolean, val likeCount: Int)
