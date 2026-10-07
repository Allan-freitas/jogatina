package br.com.jogatina.data.notifications

/**
 * Contratos espelhando os endpoints `notifications/...` da Web.Api:
 * - GET   notifications?{onlyUnread} -> [NotificationDto]
 * - PATCH notifications/{id}/read -> Guid
 * - POST  notifications/read-all -> Int (quantidade marcada)
 *
 * Tipos conhecidos: friend_request, friend_accepted, new_message.
 */

data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdOnUtc: String
)
