package com.example.saira

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SairaCommandsRobolectricTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    // -------------------------------------------------------------------------
    // Command 1: চ্যাটবক্সে ১ মিনিটের অপেক্ষা (1-Minute Chatbox Absence Greeting)
    // -------------------------------------------------------------------------
    @Test
    fun `command1 one minute absence greeting addresses sender by name and matches time of day`() {
        val morning = SairaTimeGreeting.buildOneMinuteAbsenceFallback("সজীব", "ইশতিয়াক", hourOfDay = 9)
        assertTrue(morning.startsWith("হ্যালো সজীব! সুপ্রভাত"))
        assertTrue(morning.contains("ইশতিয়াক"))

        val afternoon = SairaTimeGreeting.buildOneMinuteAbsenceFallback("Sajib", "Istiak", hourOfDay = 14)
        assertTrue(afternoon.startsWith("হ্যালো Sajib! শুভ দুপুর"))

        val evening = SairaTimeGreeting.buildOneMinuteAbsenceFallback("সজীব", "ইশতিয়াক", hourOfDay = 18)
        assertTrue(evening.startsWith("হ্যালো সজীব! শুভ সন্ধ্যা"))

        val night = SairaTimeGreeting.buildOneMinuteAbsenceFallback("সজীব", "ইশতিয়াক", hourOfDay = 23)
        assertTrue(night.startsWith("হ্যালো সজীব! শুভ রাত্রি"))
    }

    // -------------------------------------------------------------------------
    // Command 2: অনুমতিসাপেক্ষে WhatsApp ও Messenger রিপ্লাই
    // -------------------------------------------------------------------------
    @Test
    fun `command2 notification matcher enforces consent toggle platform and exact contact rule`() {
        val sajibWhatsAppRule = SairaNotificationRule(
            id = "r1",
            contactName = "Sajib",
            platform = SairaPlatform.WHATSAPP,
            taughtReplyGuidance = "আমি এখন কাজে ব্যস্ত আছি, ফ্রি হয়ে কল দেব।",
            enabled = true,
            allowLocationShare = false,
            useAiReply = false
        )
        val rahimaMessengerRule = SairaNotificationRule(
            id = "r2",
            contactName = "Rahima",
            platform = SairaPlatform.MESSENGER,
            taughtReplyGuidance = "একটু পর কথা বলছি।",
            enabled = true
        )
        val disabledRule = SairaNotificationRule(
            id = "r3",
            contactName = "Karim",
            platform = SairaPlatform.ANY,
            taughtReplyGuidance = "হ্যালো",
            enabled = false
        )
        val rules = listOf(sajibWhatsAppRule, rahimaMessengerRule, disabledRule)

        // 1. Blocked when master toggle is OFF
        assertNull(
            SairaNotificationMatcher.match(
                notifAutoReplyEnabled = false,
                liveLocationEnabled = false,
                locationPermissionGranted = false,
                packageName = "com.whatsapp",
                senderTitle = "Sajib",
                incomingText = "Kemon acho?",
                isGroupSummary = false,
                rules = rules
            )
        )

        // 2. Blocked for unlisted sender
        assertNull(
            SairaNotificationMatcher.match(
                notifAutoReplyEnabled = true,
                liveLocationEnabled = false,
                locationPermissionGranted = false,
                packageName = "com.whatsapp",
                senderTitle = "Unknown Stranger",
                incomingText = "Hi",
                isGroupSummary = false,
                rules = rules
            )
        )

        // 3. Blocked when rule exists but is disabled
        assertNull(
            SairaNotificationMatcher.match(
                notifAutoReplyEnabled = true,
                liveLocationEnabled = false,
                locationPermissionGranted = false,
                packageName = "com.whatsapp",
                senderTitle = "Karim",
                incomingText = "Hi",
                isGroupSummary = false,
                rules = rules
            )
        )

        // 4. Blocked when platform does not match (Sajib is WHATSAPP only, arrives on Messenger)
        assertNull(
            SairaNotificationMatcher.match(
                notifAutoReplyEnabled = true,
                liveLocationEnabled = false,
                locationPermissionGranted = false,
                packageName = "com.facebook.orca",
                senderTitle = "Sajib",
                incomingText = "Hello",
                isGroupSummary = false,
                rules = rules
            )
        )

        // 5. Blocked for WhatsApp/Messenger group summary notifications
        assertNull(
            SairaNotificationMatcher.match(
                notifAutoReplyEnabled = true,
                liveLocationEnabled = false,
                locationPermissionGranted = false,
                packageName = "com.whatsapp",
                senderTitle = "Sajib",
                incomingText = "2 new messages",
                isGroupSummary = true,
                rules = rules
            )
        )

        // 6. Matched for Sajib on WhatsApp (including unread message count stripped cleanly)
        val waMatch = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = true,
            liveLocationEnabled = false,
            locationPermissionGranted = false,
            packageName = "com.whatsapp",
            senderTitle = "Sajib (2 messages)",
            incomingText = "Tumi koi?",
            isGroupSummary = false,
            rules = rules
        )
        assertNotNull(waMatch)
        assertEquals(SairaPlatform.WHATSAPP, waMatch!!.platform)
        assertEquals("Sajib", waMatch.normalizedSender)

        val waReply = SairaNotificationMatcher.buildDeterministicReply(
            senderName = waMatch.normalizedSender,
            rule = waMatch.rule,
            locationSnapshot = null,
            hourOfDay = 10
        )
        assertTrue(waReply.contains("হ্যালো Sajib, সুপ্রভাত!"))
        assertTrue(waReply.contains("কাজে ব্যস্ত আছি"))

        // 7. Matched for Rahima on Messenger
        val msgMatch = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = true,
            liveLocationEnabled = false,
            locationPermissionGranted = false,
            packageName = "com.facebook.orca",
            senderTitle = "Rahima",
            incomingText = "Ki koro?",
            isGroupSummary = false,
            rules = rules
        )
        assertNotNull(msgMatch)
        assertEquals(SairaPlatform.MESSENGER, msgMatch!!.platform)

        // 8. Verify RemoteInput extraction works on realistic Android NotificationCompat/Notification action
        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setAllowFreeFormInput(true)
            .build()
        val actionIntent = android.app.PendingIntent.getBroadcast(
            application,
            0,
            Intent("com.example.ACTION_TEST_REPLY"),
            android.app.PendingIntent.FLAG_MUTABLE
        )
        val action = Notification.Action.Builder(null, "Reply", actionIntent)
            .addRemoteInput(remoteInput)
            .build()
        val notification = Notification.Builder(application, "test_ch")
            .setContentTitle("Sajib")
            .setContentText("Hello")
            .addAction(action)
            .build()
        assertNotNull(notification.actions?.firstOrNull()?.remoteInputs?.firstOrNull()?.allowFreeFormInput == true)
    }

    // -------------------------------------------------------------------------
    // Command 3: লাইভ লোকেশন শেয়ারিং (Permission + Approved Contact Gated)
    // -------------------------------------------------------------------------
    @Test
    fun `command3 live location sharing strictly requires OS permission toggle and per-contact consent`() {
        val locRuleAllowed = SairaNotificationRule(
            id = "r_loc_ok",
            contactName = "Sajib",
            platform = SairaPlatform.ANY,
            taughtReplyGuidance = "আমি এখন রাস্তায় আছি।",
            enabled = true,
            allowLocationShare = true
        )
        val locRuleDenied = locRuleAllowed.copy(id = "r_loc_no", contactName = "Kamal", allowLocationShare = false)
        val rules = listOf(locRuleAllowed, locRuleDenied)

        // Before permission granted: SairaLocationHelper refuses to access location
        assertFalse(SairaLocationHelper.hasLocationPermission(application))
        assertNull(SairaLocationHelper.getBestAvailableLocation(application))

        // Matcher refuses to attach location when permission is false
        val deniedByPerm = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = true,
            liveLocationEnabled = true,
            locationPermissionGranted = false,
            packageName = "com.whatsapp",
            senderTitle = "Sajib",
            incomingText = "তোমার লোকেশন দাও, কোথায় আছো?",
            isGroupSummary = false,
            rules = rules
        )
        assertNotNull(deniedByPerm)
        assertFalse(deniedByPerm!!.shouldAttachLocation)

        // Matcher refuses to attach location when rule.allowLocationShare is false (Kamal)
        val deniedByContactRule = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = true,
            liveLocationEnabled = true,
            locationPermissionGranted = true,
            packageName = "com.whatsapp",
            senderTitle = "Kamal",
            incomingText = "Where are you? Send live location",
            isGroupSummary = false,
            rules = rules
        )
        assertNotNull(deniedByContactRule)
        assertFalse(deniedByContactRule!!.shouldAttachLocation)

        // Matcher attaches location ONLY when all 4 gates are satisfied
        val allowedMatch = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = true,
            liveLocationEnabled = true,
            locationPermissionGranted = true,
            packageName = "com.whatsapp",
            senderTitle = "Sajib",
            incomingText = "কোথায় আছো এখন?",
            isGroupSummary = false,
            rules = rules
        )
        assertNotNull(allowedMatch)
        assertTrue(allowedMatch!!.shouldAttachLocation)

        // Grant foreground COARSE location and seed Robolectric ShadowLocationManager
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(SairaLocationHelper.hasLocationPermission(application))

        val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = Shadows.shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val mockLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 23.81033
            longitude = 90.41252
            accuracy = 24f
            time = System.currentTimeMillis()
        }
        @Suppress("DEPRECATION")
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, mockLocation)

        val snapshot = SairaLocationHelper.getBestAvailableLocation(application)
        assertNotNull(snapshot)
        assertEquals(23.81033, snapshot!!.latitude, 0.001)
        assertEquals(90.41252, snapshot.longitude, 0.001)

        val replyWithLocation = SairaNotificationMatcher.buildDeterministicReply(
            senderName = "Sajib",
            rule = locRuleAllowed,
            locationSnapshot = snapshot,
            hourOfDay = 20
        )
        assertTrue(replyWithLocation.contains("হ্যালো Sajib, শুভ সন্ধ্যা!"))
        assertTrue(replyWithLocation.contains("https://maps.google.com/?q=23.81033,90.41252"))
    }

    // -------------------------------------------------------------------------
    // Command 4: সময় অনুযায়ী সম্ভাষণ ও ফ্রি AI উত্তর
    // -------------------------------------------------------------------------
    @Test
    fun `command4 time greetings accurately classify all hours around the clock`() {
        listOf(5, 8, 11).forEach { h ->
            val info = SairaTimeGreeting.infoForHour(h)
            assertEquals("সুপ্রভাত", info.bengaliGreeting)
            assertEquals("Good Morning", info.englishGreeting)
            assertFalse(info.isNightTime)
        }
        listOf(12, 14, 16).forEach { h ->
            val info = SairaTimeGreeting.infoForHour(h)
            assertEquals("শুভ দুপুর", info.bengaliGreeting)
            assertEquals("Good Afternoon", info.englishGreeting)
            assertFalse(info.isNightTime)
        }
        listOf(17, 19, 20).forEach { h ->
            val info = SairaTimeGreeting.infoForHour(h)
            assertEquals("শুভ সন্ধ্যা", info.bengaliGreeting)
            assertEquals("Good Evening", info.englishGreeting)
            assertFalse(info.isNightTime)
        }
        listOf(21, 23, 0, 4).forEach { h ->
            val info = SairaTimeGreeting.infoForHour(h)
            assertEquals("শুভ রাত্রি", info.bengaliGreeting)
            assertEquals("Good Night", info.englishGreeting)
            assertTrue(info.isNightTime)
        }
    }
}
