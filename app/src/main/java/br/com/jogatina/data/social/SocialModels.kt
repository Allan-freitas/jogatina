package br.com.jogatina.data.social

/** Espelha FriendResponse (GET users/me/friends). */
data class FriendDto(
    val friendId: String,
    val firstName: String,
    val lastName: String,
    val isOnline: Boolean
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}
