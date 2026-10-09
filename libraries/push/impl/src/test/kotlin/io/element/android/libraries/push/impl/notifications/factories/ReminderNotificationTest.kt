/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.factories

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.ui.media.test.FakeImageLoader
import io.element.android.libraries.push.impl.notifications.RoomEventGroupInfo
import io.element.android.libraries.push.impl.notifications.artise.REMINDER_NOTIFICATION_CHANNEL_ID
import io.element.android.libraries.push.impl.notifications.fixtures.aNotifiableMessageEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableMessageEvent
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Artise: a chat's notification goes to the "Reminders" channel while it holds an important or critical Ari post. */
class ReminderNotificationTest : RobolectricTest() {
    @Test
    fun `a reminder is posted on the Reminders channel as a reminder`() = runTest {
        val sut = createNotificationCreator()
        val result = sut.createRoomNotification(events = listOf(aNotifiableMessageEvent(body = "Insulin").copy(isReminder = true)))
        assertThat(NotificationCompat.getChannelId(result)).isEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(result.category).isEqualTo(NotificationCompat.CATEGORY_REMINDER)
    }

    @Test
    fun `a plain message stays on the message channels`() = runTest {
        val sut = createNotificationCreator()
        val result = sut.createRoomNotification(events = listOf(aNotifiableMessageEvent(body = "Hi")))
        assertThat(NotificationCompat.getChannelId(result)).isNotEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(result.category).isEqualTo(NotificationCompat.CATEGORY_MESSAGE)
    }

    @Test
    fun `a reminder joining a chat's message notification moves it to the Reminders channel and keeps the earlier messages`() = runTest {
        val sut = createNotificationCreator()
        val existingNotification = sut.createRoomNotification(events = listOf(aNotifiableMessageEvent(body = "Hi")))
        val result = sut.createRoomNotification(
            events = listOf(aNotifiableMessageEvent(eventId = AN_EVENT_ID_2, body = "Insulin").copy(isReminder = true)),
            existingNotification = existingNotification,
        )
        assertThat(NotificationCompat.getChannelId(result)).isEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(result.messageTexts()).containsExactly("Hi", "Insulin").inOrder()
    }

    @Test
    fun `a message after an unread reminder keeps the chat's notification on the Reminders channel`() = runTest {
        val sut = createNotificationCreator()
        val existingNotification = sut.createRoomNotification(
            events = listOf(aNotifiableMessageEvent(body = "Insulin").copy(isReminder = true)),
        )
        val result = sut.createRoomNotification(
            events = listOf(aNotifiableMessageEvent(eventId = AN_EVENT_ID_2, body = "Hi")),
            existingNotification = existingNotification,
        )
        assertThat(NotificationCompat.getChannelId(result)).isEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(result.messageTexts()).containsExactly("Insulin", "Hi").inOrder()
    }

    private suspend fun NotificationCreator.createRoomNotification(
        events: List<NotifiableMessageEvent>,
        existingNotification: Notification? = null,
    ) = createMessagesListNotification(
        notificationAccountParams = aNotificationAccountParams(),
        roomInfo = RoomEventGroupInfo(
            sessionId = A_SESSION_ID,
            roomId = A_ROOM_ID,
            roomDisplayName = "roomDisplayName",
            hasSmartReplyError = false,
            shouldBing = false,
            customSound = null,
            isUpdated = false,
        ),
        threadId = null,
        largeIcon = null,
        lastMessageTimestamp = 123_456L,
        tickerText = "tickerText",
        existingNotification = existingNotification,
        imageLoader = FakeImageLoader(),
        events = events,
    )

    private fun Notification.messageTexts(): List<String> =
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(this)
            ?.messages
            ?.map { it.text.toString() }
            .orEmpty()
}
