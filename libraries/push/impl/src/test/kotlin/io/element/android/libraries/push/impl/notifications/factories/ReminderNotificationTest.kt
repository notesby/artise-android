/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.factories

import co.artise.android.quiet.test.FakeQuietHours
import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.ui.media.test.FakeImageLoader
import io.element.android.libraries.push.impl.notifications.DefaultNotificationDataFactory
import io.element.android.libraries.push.impl.notifications.DefaultSummaryGroupMessageCreator
import io.element.android.libraries.push.impl.notifications.RoomEventGroupInfo
import io.element.android.libraries.push.impl.notifications.RoomNotification
import io.element.android.libraries.push.impl.notifications.SummaryNotification
import io.element.android.libraries.push.impl.notifications.artise.REMINDER_NOTIFICATION_CHANNEL_ID
import io.element.android.libraries.push.impl.notifications.fake.FakeActiveNotificationsProvider
import io.element.android.libraries.push.impl.notifications.fake.FakeRoomGroupMessageCreator
import io.element.android.libraries.push.impl.notifications.fixtures.aNotifiableMessageEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableMessageEvent
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Artise: a chat's notification goes to the "Reminders" channel while it holds an important or critical Ari post, and the
 * account's summary goes with it.
 */
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

    @Test
    fun `the summary goes to the Reminders channel with the reminder it groups, and stays silent`() = runTest {
        val sut = createNotificationCreator()
        val summary = sut.createSummaryListNotification(
            notificationAccountParams = aNotificationAccountParams(),
            compatSummary = "1 notification",
            noisy = false,
            lastMessageTimestamp = 123_456L,
            onReminderChannel = true,
        )
        assertThat(NotificationCompat.getChannelId(summary)).isEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(NotificationCompat.getGroupAlertBehavior(summary)).isEqualTo(NotificationCompat.GROUP_ALERT_CHILDREN)
    }

    @Test
    fun `without reminders the summary stays on the message channels`() = runTest {
        val sut = createNotificationCreator()
        val summary = sut.createSummaryListNotification(
            notificationAccountParams = aNotificationAccountParams(),
            compatSummary = "1 notification",
            noisy = false,
            lastMessageTimestamp = 123_456L,
        )
        assertThat(NotificationCompat.getChannelId(summary)).isNotEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun `a reminder in this render or still shown puts the summary on the Reminders channel`() = runTest {
        val creator = createNotificationCreator()
        val reminder = creator.createRoomNotification(events = listOf(aNotifiableMessageEvent(body = "Insulin").copy(isReminder = true)))
        val message = creator.createRoomNotification(events = listOf(aNotifiableMessageEvent(eventId = AN_EVENT_ID_2, body = "Hi")))
        fun summaryChannel(newNotification: Notification, shown: List<Notification>): String? {
            val factory = DefaultNotificationDataFactory(
                notificationCreator = creator,
                roomGroupMessageCreator = FakeRoomGroupMessageCreator(),
                summaryGroupMessageCreator = DefaultSummaryGroupMessageCreator(FakeStringProvider("test"), creator),
                activeNotificationsProvider = FakeActiveNotificationsProvider(
                    getNotificationsForSessionResult = { shown.map { aStatusBarNotification(it) } },
                ),
                quietHours = FakeQuietHours(),
            )
            val result = factory.createSummaryNotification(
                roomNotifications = listOf(
                    RoomNotification(newNotification, A_ROOM_ID, threadId = null, messageCount = 1, latestTimestamp = 1L, shouldBing = false),
                ),
                invitationNotifications = emptyList(),
                simpleNotifications = emptyList(),
                notificationAccountParams = aNotificationAccountParams(),
            )
            return NotificationCompat.getChannelId((result as SummaryNotification.Update).notification)
        }
        assertThat(summaryChannel(reminder, shown = emptyList())).isEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(summaryChannel(message, shown = listOf(reminder))).isEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
        assertThat(summaryChannel(message, shown = listOf(message))).isNotEqualTo(REMINDER_NOTIFICATION_CHANNEL_ID)
    }

    private fun aStatusBarNotification(notification: Notification): StatusBarNotification = mockk {
        every { this@mockk.notification } returns notification
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
