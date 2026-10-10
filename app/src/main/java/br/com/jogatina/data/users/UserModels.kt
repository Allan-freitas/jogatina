package br.com.jogatina.data.users

/**
 * Espelha `GET users/me` da Web.Api (perfil do jogador logado).
 */

data class UserProfile(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val photoUrl: String? = null,
    /** ISO yyyy-MM-dd ou null. */
    val birthDate: String? = null,
    val hobbies: String? = null,
    /** ISO alpha-2 (ex.: "BR") ou null. */
    val country: String? = null
) {
    val displayName: String
        get() = firstName.ifBlank { email.substringBefore("@") }.ifBlank { "Jogador" }

    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { displayName }

    /** dd/MM/yyyy para exibição, ou null. */
    val birthDateDisplay: String?
        get() = birthDate
            ?.split("-")
            ?.takeIf { it.size == 3 }
            ?.reversed()
            ?.joinToString("/")
}
