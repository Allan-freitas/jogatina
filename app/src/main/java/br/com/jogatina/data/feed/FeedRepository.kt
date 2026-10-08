package br.com.jogatina.data.feed

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONArray
import org.json.JSONObject

class FeedRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    fun imageUrl(relative: String?): String? =
        relative?.let { api.baseUrl.trimEnd('/') + it }

    suspend fun getFeed(page: Int = 1, pageSize: Int = 20): AuthResult<List<PostDto>> =
        api.get("feed/posts?page=$page&pageSize=$pageSize", token()).map { raw ->
            parsePosts(JSONArray(raw))
        }

    suspend fun createPost(content: String?, imageUrl: String?): AuthResult<String> {
        val body = JSONObject()
        if (!content.isNullOrBlank()) body.put("content", content)
        if (!imageUrl.isNullOrBlank()) body.put("imageUrl", imageUrl)
        return api.post("feed/posts", body, token()).map { it.trim().trim('"') }
    }

    suspend fun uploadImage(bytes: ByteArray, mimeType: String): AuthResult<String> {
        val ext = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        return api.upload("feed/images", bytes, "upload.$ext", mimeType, token()).map { raw ->
            JSONObject(raw).getString("imageUrl")
        }
    }

    suspend fun setReaction(postId: String, kind: String?): AuthResult<ReactionResult> {
        val body = JSONObject()
        if (!kind.isNullOrBlank()) body.put("reaction", kind)
        return api.post("feed/posts/$postId/likes", body, token()).map { raw ->
            val json = JSONObject(raw)
            val countsJson = json.optJSONObject("counts")
            val counts = mutableMapOf<String, Int>()
            countsJson?.keys()?.forEach { key ->
                counts[key] = countsJson.optInt(key)
            }
            ReactionResult(
                myReaction = json.optString("myReaction").ifBlank { null },
                total = json.optInt("totalReactions"),
                counts = counts
            )
        }
    }

    suspend fun getComments(postId: String): AuthResult<List<CommentDto>> =
        api.get("feed/posts/$postId/comments", token()).map { raw ->
            parseComments(JSONArray(raw))
        }

    suspend fun addComment(postId: String, content: String, parentCommentId: String?): AuthResult<String> {
        val body = JSONObject().put("content", content)
        if (!parentCommentId.isNullOrBlank()) body.put("parentCommentId", parentCommentId)
        return api.post("feed/posts/$postId/comments", body, token()).map { it.trim().trim('"') }
    }

    suspend fun deletePost(postId: String): AuthResult<String> =
        api.delete("feed/posts/$postId", token()).map { it.trim().trim('"') }

    private fun parsePosts(array: JSONArray): List<PostDto> =
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            PostDto(
                id = o.getString("id"),
                authorId = o.getString("authorId"),
                authorName = o.optString("authorName", "Jogador"),
                content = o.optString("content").ifBlank { null },
                imageUrl = o.optString("imageUrl").ifBlank { null },
                createdOnUtc = o.getString("createdOnUtc"),
                totalReactions = o.optInt("totalReactions"),
                reactionCounts = o.optJSONObject("reactionCounts")?.let { countsJson ->
                    buildMap {
                        countsJson.keys().forEach { key ->
                            put(key, countsJson.optInt(key))
                        }
                    }
                }.orEmpty(),
                myReaction = o.optString("myReaction").ifBlank { null },
                commentCount = o.optInt("commentCount")
            )
        }

    private fun parseComments(array: JSONArray): List<CommentDto> =
        List(array.length()) { i -> parseComment(array.getJSONObject(i)) }

    private fun parseComment(o: JSONObject): CommentDto {
        val replies = o.optJSONArray("replies")
        return CommentDto(
            id = o.getString("id"),
            authorId = o.getString("authorId"),
            authorName = o.optString("authorName", "Jogador"),
            content = o.getString("content"),
            createdOnUtc = o.getString("createdOnUtc"),
            replies = if (replies != null) parseComments(replies) else emptyList()
        )
    }
}
