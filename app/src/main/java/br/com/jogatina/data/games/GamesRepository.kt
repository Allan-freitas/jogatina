package br.com.jogatina.data.games

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class GamesRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    fun coverUrl(relative: String?): String? =
        relative?.let { api.baseUrl.trimEnd('/') + it }

    suspend fun getGames(
        search: String? = null,
        genre: String? = null,
        platform: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): AuthResult<List<GameDto>> {
        val q = buildString {
            append("games?page=$page&pageSize=$pageSize")
            if (!search.isNullOrBlank()) append("&search=${enc(search)}")
            if (!genre.isNullOrBlank()) append("&genre=${enc(genre)}")
            if (!platform.isNullOrBlank()) append("&platform=${enc(platform)}")
        }
        // Catálogo é anônimo, mas manda o Bearer quando logado (harmless).
        return api.get(q, token()).map { raw -> parseGames(JSONArray(raw)) }
    }

    suspend fun getMyGames(): AuthResult<List<MyGameDto>> =
        api.get("users/me/games", token()).map { raw -> parseMyGames(JSONArray(raw)) }

    suspend fun addToLibrary(gameId: String, status: GameStatus, isFavorite: Boolean): AuthResult<String> {
        val body = JSONObject()
            .put("gameId", gameId)
            .put("status", status.api)
            .put("isFavorite", isFavorite)
        return api.post("users/me/games", body, token()).map { it.trim().trim('"') }
    }

    suspend fun removeFromLibrary(gameId: String): AuthResult<Unit> =
        api.delete("users/me/games/$gameId", token()).map { }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun parseGames(array: JSONArray): List<GameDto> =
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            GameDto(
                id = o.getString("id"),
                title = o.getString("title"),
                genre = o.optString("genre"),
                platform = o.optString("platform"),
                popularity = o.optInt("popularity"),
                coverImageUrl = o.optString("coverImageUrl").ifBlank { null }
            )
        }

    private fun parseMyGames(array: JSONArray): List<MyGameDto> =
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            MyGameDto(
                id = o.getString("id"),
                gameId = o.getString("gameId"),
                title = o.getString("title"),
                genre = o.optString("genre"),
                platform = o.optString("platform"),
                status = GameStatus.fromApi(o.optString("status")),
                isFavorite = o.optBoolean("isFavorite"),
                coverImageUrl = o.optString("coverImageUrl").ifBlank { null },
                addedOnUtc = o.optString("addedOnUtc")
            )
        }
}
