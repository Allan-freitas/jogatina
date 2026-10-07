package br.com.jogatina.data.games

/**
 * Contratos espelhando os endpoints `games/...` da Web.Api:
 * - GET    games?{genre,platform,minPopularity,search,page,pageSize} -> [GameDto] (anônimo)
 * - GET    users/me/games -> [MyGameDto]
 * - POST   users/me/games { gameId, status?, isFavorite? } -> Guid (409 se já está na biblioteca)
 * - DELETE users/me/games/{gameId} -> 204
 *
 * Status da API: Wishlist, Playing, Completed.
 * coverImageUrl é relativo (ex.: "/game-covers/x.jpg"); complete com a base.
 */

enum class GameStatus(val api: String, val label: String) {
    WISHLIST("Wishlist", "Quero jogar"),
    PLAYING("Playing", "Jogando"),
    COMPLETED("Completed", "Zerado");

    companion object {
        fun fromApi(value: String?): GameStatus =
            entries.firstOrNull { it.api.equals(value, ignoreCase = true) } ?: WISHLIST
    }
}

data class GameDto(
    val id: String,
    val title: String,
    val genre: String,
    val platform: String,
    val popularity: Int,
    val coverImageUrl: String?
)

data class MyGameDto(
    val id: String,
    val gameId: String,
    val title: String,
    val genre: String,
    val platform: String,
    val status: GameStatus,
    val isFavorite: Boolean,
    val coverImageUrl: String?,
    val addedOnUtc: String
)
