package br.com.jogatina.data.users

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONObject

class UserRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    fun photoUrl(relative: String?): String? =
        relative?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let { api.baseUrl.trimEnd('/') + it }

    suspend fun getMyProfile(): AuthResult<UserProfile> =
        api.get("users/me", token()).map(::parseProfile)

    suspend fun updateProfile(birthDateIso: String?, hobbies: String?, country: String?): AuthResult<UserProfile> {
        val body = JSONObject()
        if (birthDateIso != null) body.put("birthDate", birthDateIso) else body.put("birthDate", JSONObject.NULL)
        if (hobbies != null) body.put("hobbies", hobbies) else body.put("hobbies", JSONObject.NULL)
        if (country != null) body.put("country", country) else body.put("country", JSONObject.NULL)
        return api.put("users/me", body, token()).map(::parseProfile)
    }

    suspend fun uploadPhoto(bytes: ByteArray, mimeType: String): AuthResult<String> {
        val ext = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        return api.upload("users/me/photo", bytes, "photo.$ext", mimeType, token()).map { raw ->
            JSONObject(raw).getString("photoUrl")
        }
    }

    private fun parseProfile(raw: String): UserProfile {
        val o = JSONObject(raw)
        return UserProfile(
            id = o.getString("id"),
            email = o.optString("email"),
            firstName = o.optString("firstName"),
            lastName = o.optString("lastName"),
            photoUrl = o.optString("photoUrl").ifBlank { null },
            birthDate = o.optString("birthDate").ifBlank { null },
            hobbies = o.optString("hobbies").ifBlank { null },
            country = o.optString("country").ifBlank { null }
        )
    }
}
