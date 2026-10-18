/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.artise

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutomationLevelTest {
    @Test
    fun `a critical post reads as critical and belongs on the Reminders channel`() {
        val level = AutomationLevel.fromRawEvent(aPost(level = "critical"))
        assertThat(level).isEqualTo(AutomationLevel.CRITICAL)
        assertThat(level?.isReminder).isTrue()
    }

    @Test
    fun `an important post reads as important and belongs on the Reminders channel`() {
        val level = AutomationLevel.fromRawEvent(aPost(level = "important"))
        assertThat(level).isEqualTo(AutomationLevel.IMPORTANT)
        assertThat(level?.isReminder).isTrue()
    }

    @Test
    fun `a normal post stays with the chat's messages`() {
        val level = AutomationLevel.fromRawEvent(aPost(level = "normal"))
        assertThat(level).isEqualTo(AutomationLevel.NORMAL)
        assertThat(level?.isReminder).isFalse()
    }

    @Test
    fun `an unknown or missing level from a newer bot counts as normal`() {
        assertThat(AutomationLevel.fromRawEvent(aPost(level = "urgent"))).isEqualTo(AutomationLevel.NORMAL)
        assertThat(AutomationLevel.fromRawEvent(aPost(level = null))).isEqualTo(AutomationLevel.NORMAL)
    }

    @Test
    fun `a message without the field is not an automation post`() {
        val message = """{"type":"m.room.message","content":{"msgtype":"m.text","body":"Hi"}}"""
        assertThat(AutomationLevel.fromRawEvent(message)).isNull()
    }

    @Test
    fun `the field outside the content, a still encrypted event, or broken JSON give no level`() {
        val outside = """{"type":"m.room.message","co.artise.automation":{"level":"critical"},"content":{"body":"x"}}"""
        val encrypted = """{"type":"m.room.encrypted","content":{"algorithm":"m.megolm.v1.aes-sha2","ciphertext":"AwgA"}}"""
        assertThat(AutomationLevel.fromRawEvent(outside)).isNull()
        assertThat(AutomationLevel.fromRawEvent(encrypted)).isNull()
        assertThat(AutomationLevel.fromRawEvent("""{"content":{"co.artise.automation":""")).isNull()
        assertThat(AutomationLevel.fromRawEvent("""{"content":{"co.artise.automation":"critical"}}""")).isNull()
        assertThat(AutomationLevel.fromRawEvent(null)).isNull()
        assertThat(AutomationLevel.fromRawEvent("")).isNull()
    }

    private fun aPost(level: String?): String {
        val levelField = level?.let { ""","level":"$it"""" }.orEmpty()
        return """{"type":"m.room.message","event_id":"${'$'}an_event","content":{"msgtype":"m.text","body":"💊 Toma tu pastilla",""" +
            """"co.artise.automation":{"id":"a1"$levelField}}}"""
    }
}
