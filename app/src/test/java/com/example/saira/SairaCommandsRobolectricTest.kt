package com.example.saira

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
    }

    // -------------------------------------------------------------------------
    // End-to-End Service + DataStore + RemoteInput Broadcast Verification
    // -------------------------------------------------------------------------
    @Test
    fun `notification listener service end to end dispatches remote input and records log only for consented contact`() = runBlocking {
        val prefs = AppPreferencesRepository(application)
        prefs.clearAutoReplyLogs()
        prefs.setSairaNotifReplyEnabled(true)
        prefs.setSairaLiveLocationEnabled(true)
        prefs.setPeerLocationAllowed("peer_sajib", true)
        prefs.saveSairaNotificationRules(
            listOf(
                SairaNotificationRule(
                    id = "rule_sajib",
                    contactName = "সজীব",
                    platform = SairaPlatform.WHATSAPP,
                    taughtReplyGuidance = "আমি একটু বাইরে আছি।",
                    enabled = true,
                    allowLocationShare = true,
                    useAiReply = false
                )
            )
        )

        assertTrue(prefs.sairaNotifReplyEnabled.first())
        assertTrue(prefs.sairaLiveLocationEnabled.first())
        assertTrue("peer_sajib" in prefs.sairaLiveLocationAllowedPeers.first())

        // Grant coarse location permission & seed ShadowLocationManager
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = Shadows.shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val mockLoc = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 23.78088
            longitude = 90.27923
            accuracy = 18f
            time = System.currentTimeMillis()
        }
        @Suppress("DEPRECATION")
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, mockLoc)

        // Verify notification listener permission check helper
        val componentFlat = ComponentName(application, SairaNotificationListenerService::class.java).flattenToString()
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", componentFlat)
        assertTrue(SairaNotificationListenerService.isNotificationAccessGranted(application))

        // Capture dispatched RemoteInput reply via BroadcastReceiver
        var capturedReply: String? = null
        val replyActionIntent = "com.example.TEST_WHATSAPP_REMOTE_INPUT"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val results = RemoteInput.getResultsFromIntent(intent)
                capturedReply = results?.getCharSequence("key_reply")?.toString()
            }
        }
        application.registerReceiver(receiver, IntentFilter(replyActionIntent), Context.RECEIVER_EXPORTED)

        fun buildSbn(pkg: String, sender: String, text: String): StatusBarNotification {
            val remoteInput = RemoteInput.Builder("key_reply").setAllowFreeFormInput(true).build()
            val pendingIntent = PendingIntent.getBroadcast(
                application,
                202,
                Intent(replyActionIntent).setPackage(application.packageName),
                PendingIntent.FLAG_MUTABLE
            )
            val action = Notification.Action.Builder(null, "Reply", pendingIntent)
                .addRemoteInput(remoteInput)
                .build()
            val notif = Notification.Builder(application, "wa_ch")
                .setContentTitle(sender)
                .setContentText(text)
                .addAction(action)
                .build()
            @Suppress("DEPRECATION")
            return StatusBarNotification(
                pkg,
                pkg,
                501,
                null,
                Process.myUid(),
                0,
                0,
                notif,
                Process.myUserHandle(),
                System.currentTimeMillis()
            )
        }

        val service = Robolectric.buildService(SairaNotificationListenerService::class.java).create().get()

        // 1) Unlisted sender -> MUST NOT reply
        service.processNotification(buildSbn("com.whatsapp", "Stranger", "কই আছো?"))
        Shadows.shadowOf(application.mainLooper).idle()
        assertNull(capturedReply)
        assertTrue(prefs.sairaAutoReplyLogs.first().isEmpty())

        // 2) Approved sender asking location -> MUST reply with taught message + Maps location link & log it
        service.processNotification(buildSbn("com.whatsapp", "সজীব", "তোমার লোকেশন দাও, কই আছো?"))
        Shadows.shadowOf(application.mainLooper).idle()

        assertNotNull(capturedReply)
        assertTrue(capturedReply!!.contains("হ্যালো সজীব"))
        assertTrue(capturedReply!!.contains("আমি একটু বাইরে আছি।"))
        assertTrue(capturedReply!!.contains("https://maps.google.com/?q=23.78088,90.27923"))

        val logs = prefs.sairaAutoReplyLogs.first()
        assertEquals(1, logs.size)
        assertEquals("সজীব", logs.first().contactName)
        assertEquals("WhatsApp", logs.first().platformLabel)
        assertTrue(logs.first().includedLocation)

        application.unregisterReceiver(receiver)
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

        assertFalse(SairaLocationHelper.hasLocationPermission(application))
        assertNull(SairaLocationHelper.getBestAvailableLocation(application))

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
