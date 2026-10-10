package br.com.jogatina.data.social

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

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

    suspend fun searchUsers(query: String): AuthResult<List<SearchedUser>> {
        val enc = URLEncoder.encode(query, Charsets.UTF_8.name())
        return api.get("users/search?query=$enc&page=1&pageSize=20", token()).map { raw ->
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SearchedUser(
                    id = o.getString("id"),
                    firstName = o.optString("firstName"),
                    lastName = o.optString("lastName"),
                    email = o.optString("email")
                )
            }
        }
    }

    suspend fun getPendingRequests(): AuthResult<List<PendingRequest>> =
        api.get("social/friend-requests", token()).map { raw ->
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                PendingRequest(
                    requestId = o.getString("requestId"),
                    otherUserId = o.getString("otherUserId"),
                    otherName = o.optString("otherName", "Jogador"),
                    incoming = o.optBoolean("incoming"),
                    createdOnUtc = o.optString("createdOnUtc")
                )
            }
        }

    suspend fun sendRequest(addresseeId: String): AuthResult<String> {
        val body = JSONObject().put("addresseeId", addresseeId)
        return api.post("social/friend-requests", body, token()).map { it.trim().trim('"') }
    }

    suspend fun respondRequest(requestId: String, accept: Boolean): AuthResult<Unit> {
        val body = JSONObject().put("action", if (accept) "Accept" else "Decline")
        return api.put("social/friend-requests/$requestId", body, token()).map { }
    }

    suspend fun removeFriend(friendId: String): AuthResult<Unit> =
        api.delete("social/friends/$friendId", token()).map { }
}
