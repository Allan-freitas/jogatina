package br.com.jogatina.data.social

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONArray

class FriendsRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    suspend fun getFriends(): AuthResult<List<FriendDto>> =
        api.get("users/me/friends", token()).map { raw ->
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                FriendDto(
                    friendId = o.getString("friendId"),
                    firstName = o.optString("firstName"),
                    lastName = o.optString("lastName"),
                    isOnline = o.optBoolean("isOnline")
                )
            }
        }
}
