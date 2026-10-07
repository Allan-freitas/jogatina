package br.com.jogatina.data.notifications

import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.map
import org.json.JSONArray

class NotificationsRepository(
    private val api: ApiClient,
    private val token: () -> String?
) {
    suspend fun getNotifications(onlyUnread: Boolean = false): AuthResult<List<NotificationDto>> {
        val path = if (onlyUnread) "notifications?onlyUnread=true" else "notifications"
        return api.get(path, token()).map { raw -> parse(JSONArray(raw)) }
    }

    suspend fun markAsRead(id: String): AuthResult<String> =
        api.patchEmpty("notifications/$id/read", token()).map { it.trim().trim('"') }

    suspend fun markAllAsRead(): AuthResult<Int> =
        api.postEmpty("notifications/read-all", token()).map { it.trim().toInt() }

    private fun parse(array: JSONArray): List<NotificationDto> =
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            NotificationDto(
                id = o.getString("id"),
                type = o.optString("type"),
                title = o.optString("title"),
                body = o.optString("body"),
                isRead = o.optBoolean("isRead"),
                createdOnUtc = o.optString("createdOnUtc")
            )
        }
}
