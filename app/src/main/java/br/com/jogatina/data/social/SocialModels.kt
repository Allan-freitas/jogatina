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

/** Espelha SearchedUserResponse (GET users/search). */
data class SearchedUser(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

/** Espelha PendingRequestResponse (GET social/friend-requests). */
data class PendingRequest(
    val requestId: String,
    val otherUserId: String,
    val otherName: String,
    val incoming: Boolean,
    val createdOnUtc: String
)
