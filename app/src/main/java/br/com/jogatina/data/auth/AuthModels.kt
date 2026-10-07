package br.com.jogatina.data.auth

/**
 * Contratos espelhando `E:\Projetos\Jogatina\CleanArchitecture.slnx`:
 * - Web.Api/Endpoints/Users/Login.cs    -> POST auth/login
 * - Web.Api/Endpoints/Users/Register.cs -> POST auth/register
 * - Web.Api/Endpoints/Users/SocialLogin.cs -> POST auth/social
 * - Application/Users/AccessTokensResponse.cs -> { accessToken, refreshToken }
 */

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
    val password: String
)

data class SocialLoginRequest(
    val provider: String,
    val token: String,
    val providerUserId: String? = null
)

data class AccessTokensResponse(
    val accessToken: String,
    val refreshToken: String
)

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Error(val message: String, val statusCode: Int? = null) : AuthResult<Nothing>
}

inline fun <T, R> AuthResult<T>.map(transform: (T) -> R): AuthResult<R> =
    when (this) {
        is AuthResult.Success -> try {
            AuthResult.Success(transform(value))
        } catch (e: Exception) {
            AuthResult.Error("Resposta inválida da API: ${e.message}")
        }
        is AuthResult.Error -> this
    }
