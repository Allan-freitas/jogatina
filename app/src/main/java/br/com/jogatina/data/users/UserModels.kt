package br.com.jogatina.data.users

/**
 * Espelha `GET users/me` da Web.Api (perfil do jogador logado).
 */

data class UserProfile(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String
) {
    val displayName: String
        get() = firstName.ifBlank { email.substringBefore("@") }.ifBlank { "Jogador" }
}
