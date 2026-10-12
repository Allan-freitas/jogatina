package br.com.jogatina.ui.notifications

import br.com.jogatina.data.notifications.NotificationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsUiStateTest {

    private fun notification(id: String, read: Boolean, created: String = "2026-01-01T00:00:00Z") =
        NotificationDto(
            id = id,
            type = "friend_request",
            title = "title",
            body = "body",
            isRead = read,
            createdOnUtc = created,
            referenceType = null,
            referenceId = null
        )

    @Test
    fun todas_mostram_todas_inclusive_as_nao_lidas() {
        val state = NotificationsUiState(
            notifications = listOf(
                notification("1", read = false),
                notification("2", read = true),
                notification("3", read = false)
            )
        )

        assertEquals(2, state.unreadCount)
        assertEquals(3, state.visible.size)
        assertTrue(state.visible.any { it.id == "1" })
        assertTrue(state.visible.any { it.id == "3" })
    }

    @Test
    fun nao_lidas_filtram_apenas_as_nao_lidas() {
        val state = NotificationsUiState(
            notifications = listOf(
                notification("1", read = false),
                notification("2", read = true)
            ),
            onlyUnread = true
        )

        assertEquals(1, state.visible.size)
        assertEquals("1", state.visible.first().id)
    }
}
