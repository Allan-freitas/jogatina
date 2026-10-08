package br.com.jogatina.data.users

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONObject

class UserRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    suspend fun getMyProfile(): AuthResult<UserProfile> =
        api.get("users/me", token()).map { raw ->
            val o = JSONObject(raw)
            UserProfile(
                id = o.getString("id"),
                email = o.optString("email"),
                firstName = o.optString("firstName"),
                lastName = o.optString("lastName")
            )
        }
}
