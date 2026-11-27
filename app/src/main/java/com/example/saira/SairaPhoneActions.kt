package com.example.saira

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.ai.SairaAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class ContactMatch(val name: String, val number: String)

data class PhoneOutcome(
    val ok: Boolean,
    val message: String,
    /** Saira Automation (accessibility) would let her finish this herself. */
    val needsAutomation: Boolean = false,
    /** Notification access would let her answer/decline app calls. */
    val needsNotificationAccess: Boolean = false
)

/**
 * Executes [PhoneCommand]s on the device: phone calls, SMS, WhatsApp / Telegram /
 * Messenger / imo / Viber calls and messages, answering / declining calls, opening apps
 * and turning the screen on / off. Used by both the Saira chat and voice mode.
 */
object SairaPhoneActions {
    private const val TAG = "SairaPhone"

    // ------------------------------------------------------------------ permissions
    fun isDeclared(context: Context, permission: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.contains(permission) == true
    }.getOrDefault(false)

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Runtime permissions worth asking for before running [cmd] (only ones this build declares). */
    fun permissionsFor(context: Context, cmd: PhoneCommand): List<String> {
        val wanted = ArrayList<String>()
        val needsContacts = !cmd.targetIsNumber && cmd.target.isNotBlank() &&
            cmd.app in setOf(PhoneApp.PHONE, PhoneApp.SMS, PhoneApp.WHATSAPP, PhoneApp.TELEGRAM, PhoneApp.VIBER)
        if (needsContacts) wanted.add(Manifest.permission.READ_CONTACTS)
        when (cmd.action) {
            PhoneAction.CALL, PhoneAction.VIDEO_CALL -> {
                if (cmd.app == PhoneApp.PHONE) wanted.add(Manifest.permission.CALL_PHONE)
                if (cmd.app == PhoneApp.WHATSAPP) wanted.add(Manifest.permission.READ_CONTACTS)
            }
            PhoneAction.MESSAGE -> if (cmd.app == PhoneApp.SMS) wanted.add(Manifest.permission.SEND_SMS)
            PhoneAction.ANSWER, PhoneAction.DECLINE -> wanted.add(Manifest.permission.ANSWER_PHONE_CALLS)
            else -> Unit
        }
        return wanted.distinct().filter { isDeclared(context, it) && !granted(context, it) }
    }

    /** Everything voice mode may need, asked once when the user turns it on. */
    fun voiceModePermissions(context: Context): List<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.filter { isDeclared(context, it) && !granted(context, it) }
    }

    // ------------------------------------------------------------------ AI fallback
    private const val EXTRACT_SYSTEM = """You turn a user's request to their phone assistant into JSON. Reply with ONLY one JSON object, no prose:
{"action":"call|video_call|message|answer|decline|open_app|screen_on|screen_off|none","app":"phone|sms|whatsapp|messenger|telegram|imo|viber|other","target":"contact name or phone number exactly as the user said it (app name for open_app)","text":"exact message to send, in the user's language"}
Use "none" when the user is not asking the phone to do one of these actions right now (questions, stories, past/future statements)."""

    /** Asks the AI to read a command the local rules could not (null = not a command / offline). */
    suspend fun extractWithAi(userText: String): PhoneCommand? = withContext(Dispatchers.IO) {
        val raw = withTimeoutOrNull(14_000L) {
            runCatching { SairaAiClient.complete(EXTRACT_SYSTEM, emptyList(), userText) }.getOrNull()
        } ?: return@withContext null
        parseAiJson(raw)
    }

    fun parseAiJson(raw: String): PhoneCommand? = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = JSONObject(raw.substring(start, end + 1))
        SairaPhoneCommandParser.fromFields(
            o.optString("action"), o.optString("app"), o.optString("target"), o.optString("text")
        )
    }.getOrNull()

    // ------------------------------------------------------------------ contacts
    @SuppressLint("Range")
    fun findContacts(context: Context, query: String, limit: Int = 5): List<ContactMatch> {
        val q = query.trim()
        if (q.isEmpty() || !granted(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val found = LinkedHashMap<String, Pair<ContactMatch, Int>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val digits = SairaPhoneCommandParser.digitsOf(number)
                    if (digits.length < 5) continue
                    val key = digits.takeLast(10)
                    val lowName = name.lowercase()
                    val lowQ = q.lowercase()
                    val score = when {
                        lowName == lowQ -> 0
                        lowName.startsWith("$lowQ ") || lowName.startsWith(lowQ) -> 1
                        lowName.split(' ').any { it == lowQ } -> 2
                        else -> 3
                    }
                    val prev = found[key]
                    if (prev == null || score < prev.second) found[key] = ContactMatch(name, number) to score
                }
            }
        }.onFailure { Log.w(TAG, "contact lookup failed", it) }
        return found.values.sortedWith(compareBy({ it.second }, { it.first.name.length })).map { it.first }.take(limit)
    }

    // ------------------------------------------------------------------ helpers
    fun installedPackage(context: Context, app: PhoneApp): String? = app.packages.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    private fun simIso(context: Context): String? = runCatching {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simCountryIso
    }.getOrNull()

    private fun start(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent); true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "start blocked", e); false
        } catch (e: Exception) {
            Log.w(TAG, "start failed", e); false
        }
    }

    private fun isLocked(context: Context): Boolean = runCatching {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.isKeyguardLocked && km.isDeviceSecure
    }.getOrDefault(false)

    private fun lockedHint(context: Context): String =
        if (isLocked(context)) " (ফোন লক করা থাকলে আগে আনলক করুন।)" else ""

    // ------------------------------------------------------------------ execute
    /**
     * Runs an already-confirmed command. [number] is the resolved phone number (if any),
     * [who] the display name used in messages, [tag] lets automation report back.
     */
    fun execute(context: Context, cmd: PhoneCommand, number: String?, who: String, tag: String = ""): PhoneOutcome {
        return when (cmd.action) {
            PhoneAction.ANSWER -> answerCall(context)
            PhoneAction.DECLINE -> declineCall(context)
            PhoneAction.SCREEN_ON -> screenOn(context)
            PhoneAction.SCREEN_OFF -> screenOff(context)
            PhoneAction.OPEN_APP -> openApp(context, cmd)
            PhoneAction.CALL, PhoneAction.VIDEO_CALL, PhoneAction.MESSAGE -> when (cmd.app) {
                PhoneApp.PHONE, PhoneApp.OTHER -> phoneCall(context, number, who)
                PhoneApp.SMS -> sendSms(context, number, who, cmd.text, tag)
                PhoneApp.WHATSAPP -> whatsapp(context, cmd, number, who, tag)
                PhoneApp.TELEGRAM -> telegram(context, cmd, number, who, tag)
                PhoneApp.MESSENGER, PhoneApp.IMO, PhoneApp.VIBER -> searchAndAct(context, cmd, who, tag)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun phoneCall(context: Context, number: String?, who: String): PhoneOutcome {
        if (number.isNullOrBlank()) return PhoneOutcome(false, "$who-এর নম্বর পাইনি।")
        val uri = Uri.fromParts("tel", number, null)
        if (granted(context, Manifest.permission.CALL_PHONE)) {
            // TelecomManager.placeCall works even from the background (voice mode, screen off).
            val placed = runCatching {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.placeCall(uri, Bundle())
            }.isSuccess
            if (placed) return PhoneOutcome(true, "📞 $who ($number)-কে কল দিচ্ছি।")
            if (start(context, Intent(Intent.ACTION_CALL, uri))) return PhoneOutcome(true, "📞 $who ($number)-কে কল দিচ্ছি।")
        }
        return if (start(context, Intent(Intent.ACTION_DIAL, uri))) {
            PhoneOutcome(true, "📞 ডায়ালারে $number বসিয়ে দিয়েছি — সবুজ কল বাটনে চাপুন। (Phone অনুমতি দিলে পরের বার সরাসরি কল দেব।)")
        } else PhoneOutcome(false, "কল দিতে পারিনি — ফোনে ডায়ালার অ্যাপ পাইনি।")
    }

    @SuppressLint("MissingPermission")
    private fun sendSms(context: Context, number: String?, who: String, text: String, tag: String): PhoneOutcome {
        if (number.isNullOrBlank()) return PhoneOutcome(false, "$who-এর নম্বর পাইনি।")
        if (text.isBlank()) return PhoneOutcome(false, "কী লিখব সেটা বলুন।")
        if (granted(context, Manifest.permission.SEND_SMS)) {
            val sent = runCatching {
                val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION") SmsManager.getDefault()
                }
                val parts = sms.divideMessage(text)
                sms.sendMultipartTextMessage(number, null, parts, null, null)
            }.isSuccess
            if (sent) return PhoneOutcome(true, "✉️ $who ($number)-কে SMS পাঠিয়েছি: \"$text\"")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
            .putExtra("sms_body", text)
        if (!start(context, intent)) return PhoneOutcome(false, "SMS অ্যাপ খুলতে পারিনি।")
        val smsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        return if (SairaAutomation.isReady(context)) {
            SairaAutomation.start(
                AutoTask(
                    tag = tag,
                    label = "SMS → $who",
                    packages = setOfNotNull(smsPkg),
                    steps = listOf(AutoStep.Click(descs = SEND_LABELS, texts = SEND_LABELS, ids = SMS_SEND_IDS))
                )
            )
            PhoneOutcome(true, "✉️ $who-এর SMS লিখে দিয়েছি, Send চাপছি…")
        } else PhoneOutcome(
            true, "✉️ $who-এর জন্য SMS লিখে রেখেছি — শুধু Send চাপুন।", needsAutomation = SairaAutomation.isAvailable(context)
        )
    }

    private fun whatsapp(context: Context, cmd: PhoneCommand, number: String?, who: String, tag: String): PhoneOutcome {
        val pkg = installedPackage(context, PhoneApp.WHATSAPP) ?: return PhoneOutcome(false, "ফোনে WhatsApp ইনস্টল করা নেই।")
        if (number.isNullOrBlank()) return PhoneOutcome(false, "$who-এর নম্বর পাইনি — WhatsApp-এর জন্য নম্বর লাগবে।")
        val intl = SairaPhoneCommandParser.internationalDigits(number, simIso(context))
        val video = cmd.action == PhoneAction.VIDEO_CALL
        if (cmd.action == PhoneAction.MESSAGE) {
            val url = "https://api.whatsapp.com/send?phone=$intl&text=${Uri.encode(cmd.text)}"
            if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) {
                return PhoneOutcome(false, "WhatsApp খুলতে পারিনি।" + automationHint(context))
            }
            return if (SairaAutomation.isReady(context)) {
                SairaAutomation.start(
                    AutoTask(
                        tag = tag, label = "WhatsApp মেসেজ → $who", packages = setOf(pkg),
                        steps = listOf(AutoStep.Click(descs = SEND_LABELS, ids = listOf("$pkg:id/send")))
                    )
                )
                PhoneOutcome(true, "💬 WhatsApp-এ $who-কে মেসেজ লিখেছি, Send করছি…" + lockedHint(context))
            } else PhoneOutcome(
                true, "💬 WhatsApp-এ $who-এর চ্যাটে মেসেজ লিখে রেখেছি — শুধু Send চাপুন।",
                needsAutomation = SairaAutomation.isAvailable(context)
            )
        }
        // Calls: the contact's WhatsApp call row starts the call directly.
        whatsappCallDataId(context, intl, video)?.let { id ->
            val mime = if (video) WA_VIDEO_MIME else WA_VOICE_MIME
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://com.android.contacts/data/$id"), mime)
                .setPackage(pkg)
            if (start(context, intent)) {
                return PhoneOutcome(true, (if (video) "🎥" else "📞") + " WhatsApp-এ $who-কে ${if (video) "ভিডিও " else ""}কল দিচ্ছি।" + lockedHint(context))
            }
        }
        // Otherwise open the chat and tap the call button.
        if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$intl")).setPackage(pkg))) {
            return PhoneOutcome(false, "WhatsApp খুলতে পারিনি।" + automationHint(context))
        }
        return if (SairaAutomation.isReady(context)) {
            SairaAutomation.start(
                AutoTask(
                    tag = tag, label = "WhatsApp কল → $who", packages = setOf(pkg),
                    steps = listOf(
                        AutoStep.Click(descs = if (video) VIDEO_CALL_LABELS else VOICE_CALL_LABELS),
                        AutoStep.Click(texts = CALL_CONFIRM_LABELS, optional = true)
                    )
                )
            )
            PhoneOutcome(true, "📞 WhatsApp-এ $who-এর চ্যাট খুলে কল বাটন চাপছি…" + lockedHint(context))
        } else PhoneOutcome(
            true, "WhatsApp-এ $who-এর চ্যাট খুলেছি — ওপরের ${if (video) "ভিডিও" else "ফোন"} আইকনে চাপুন।",
            needsAutomation = SairaAutomation.isAvailable(context)
        )
    }

    private fun telegram(context: Context, cmd: PhoneCommand, number: String?, who: String, tag: String): PhoneOutcome {
        val pkg = installedPackage(context, PhoneApp.TELEGRAM) ?: return PhoneOutcome(false, "ফোনে Telegram ইনস্টল করা নেই।")
        if (number.isNullOrBlank()) return searchAndAct(context, cmd, who, tag)
        val intl = SairaPhoneCommandParser.internationalDigits(number, simIso(context))
        if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$intl")).setPackage(pkg))) {
            return PhoneOutcome(false, "Telegram খুলতে পারিনি।" + automationHint(context))
        }
        if (!SairaAutomation.isReady(context)) {
            return PhoneOutcome(true, "Telegram-এ $who-এর চ্যাট খুলেছি — বাকিটা আপনি চাপুন।", needsAutomation = SairaAutomation.isAvailable(context))
        }
        SairaAutomation.start(AutoTask(tag = tag, label = "Telegram → $who", packages = setOf(pkg), steps = actSteps(cmd)))
        return PhoneOutcome(true, "Telegram-এ $who-এর চ্যাট খুলে কাজটা করছি…" + lockedHint(context))
    }

    /** Messenger / imo / Viber (and Telegram by name): open the app, search the name, act. */
    private fun searchAndAct(context: Context, cmd: PhoneCommand, who: String, tag: String): PhoneOutcome {
        val pkg = installedPackage(context, cmd.app) ?: return PhoneOutcome(false, "ফোনে ${cmd.app.label} ইনস্টল করা নেই।")
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return PhoneOutcome(false, "${cmd.app.label} খুলতে পারিনি।")
        if (!SairaAutomation.isReady(context)) {
            start(context, launch)
            return PhoneOutcome(
                false,
                "${cmd.app.label} খুলেছি। নিজে খুঁজে ${cmd.app.label}-এ কাজ করতে Saira Automation চালু করতে হবে।",
                needsAutomation = SairaAutomation.isAvailable(context)
            )
        }
        val name = cmd.target.ifBlank { who }
        if (!start(context, launch)) return PhoneOutcome(false, "${cmd.app.label} খুলতে পারিনি।")
        SairaAutomation.start(
            AutoTask(
                tag = tag, label = "${cmd.app.label} → $name", packages = setOf(pkg), timeoutMs = 35_000,
                steps = listOf(
                    AutoStep.Click(descs = SEARCH_LABELS, texts = SEARCH_LABELS, optional = true),
                    AutoStep.TypeText(name),
                    AutoStep.PickResult(name)
                ) + actSteps(cmd)
            )
        )
        return PhoneOutcome(true, "${cmd.app.label}-এ $name-কে খুঁজে কাজটা করছি…" + lockedHint(context))
    }

    private fun actSteps(cmd: PhoneCommand): List<AutoStep> = when (cmd.action) {
        PhoneAction.MESSAGE -> listOf(AutoStep.TypeText(cmd.text), AutoStep.Click(descs = SEND_LABELS, texts = SEND_LABELS))
        PhoneAction.VIDEO_CALL -> listOf(
            AutoStep.Click(descs = VIDEO_CALL_LABELS, texts = VIDEO_CALL_LABELS),
            AutoStep.Click(texts = CALL_CONFIRM_LABELS, optional = true)
        )
        else -> listOf(
            AutoStep.Click(descs = VOICE_CALL_LABELS, texts = VOICE_CALL_LABELS),
            AutoStep.Click(texts = CALL_CONFIRM_LABELS, optional = true)
        )
    }

    private fun automationHint(context: Context): String =
        if (SairaAutomation.isAvailable(context) && !SairaAutomation.isReady(context)) {
            " (স্ক্রিন বন্ধ / অ্যাপ বন্ধ থাকা অবস্থায় অন্য অ্যাপ খুলতে Saira Automation চালু করুন।)"
        } else ""

    private fun whatsappCallDataId(context: Context, intlDigits: String, video: Boolean): Long? {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return null
        val mime = if (video) WA_VIDEO_MIME else WA_VOICE_MIME
        val tail = intlDigits.takeLast(10)
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(mime),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val data = c.getString(1).orEmpty()
                    if (SairaPhoneCommandParser.digitsOf(data.substringBefore('@')).endsWith(tail)) return@use c.getLong(0)
                }
                null
            }
        }.getOrNull()
    }

    // ------------------------------------------------------------------ calls in progress
    @SuppressLint("MissingPermission")
    fun answerCall(context: Context): PhoneOutcome {
        SairaNotificationListenerService.fireCallAction(context, answer = true)?.let { app ->
            return PhoneOutcome(true, "📞 $app কল ধরেছি।")
        }
        if (granted(context, Manifest.permission.ANSWER_PHONE_CALLS)) {
            val ok = runCatching {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                @Suppress("DEPRECATION") tm.acceptRingingCall()
            }.isSuccess
            if (ok) return PhoneOutcome(true, "📞 ফোন কল ধরেছি (যদি কোনো কল বাজছিল)।")
        }
        return PhoneOutcome(
            false,
            "কল ধরতে পারিনি। ফোন কলের জন্য 'Phone calls' অনুমতি, আর WhatsApp/Messenger কলের জন্য Notification access লাগবে।",
            needsNotificationAccess = SairaNotificationListenerService.isListenerAvailable(context) &&
                !SairaNotificationListenerService.isNotificationAccessGranted(context)
        )
    }

    @SuppressLint("MissingPermission")
    fun declineCall(context: Context): PhoneOutcome {
        SairaNotificationListenerService.fireCallAction(context, answer = false)?.let { app ->
            return PhoneOutcome(true, "📵 $app কল কেটে দিয়েছি।")
        }
        if (granted(context, Manifest.permission.ANSWER_PHONE_CALLS)) {
            val ended = runCatching {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                @Suppress("DEPRECATION") tm.endCall()
            }.getOrDefault(false)
            if (ended) return PhoneOutcome(true, "📵 কল কেটে দিয়েছি।")
        }
        return PhoneOutcome(
            false, "কোনো কল কাটতে পারিনি — হয়তো এখন কোনো কল চলছে না।",
            needsNotificationAccess = SairaNotificationListenerService.isListenerAvailable(context) &&
                !SairaNotificationListenerService.isNotificationAccessGranted(context)
        )
    }

    // ------------------------------------------------------------------ screen
    @SuppressLint("WakelockTimeout")
    fun screenOn(context: Context): PhoneOutcome {
        if (!granted(context, Manifest.permission.WAKE_LOCK)) {
            return PhoneOutcome(false, "স্ক্রিন অন করতে Full edition লাগবে।")
        }
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Chatbox:SairaScreenOn"
            )
            wl.acquire(4_000L)
            PhoneOutcome(true, "💡 স্ক্রিন অন করেছি।")
        }.getOrElse { PhoneOutcome(false, "স্ক্রিন অন করতে পারিনি।") }
    }

    fun screenOff(context: Context): PhoneOutcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SairaAutomation.lockScreen()) {
            return PhoneOutcome(true, "🌙 স্ক্রিন বন্ধ করেছি।")
        }
        return PhoneOutcome(
            false, "স্ক্রিন বন্ধ করতে Saira Automation চালু করতে হবে।",
            needsAutomation = SairaAutomation.isAvailable(context)
        )
    }

    // ------------------------------------------------------------------ apps
    private fun openApp(context: Context, cmd: PhoneCommand): PhoneOutcome {
        val pkg = if (cmd.app != PhoneApp.OTHER) installedPackage(context, cmd.app) else findAppByName(context, cmd.target)
        val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return PhoneOutcome(false, "\"${cmd.target}\" নামে কোনো অ্যাপ পাইনি।")
        return if (start(context, launch)) PhoneOutcome(true, "📱 ${cmd.target} খুলেছি।")
        else PhoneOutcome(false, "${cmd.target} খুলতে পারিনি।" + automationHint(context))
    }

    private fun findAppByName(context: Context, name: String): String? {
        val q = name.lowercase().removeSuffix(" app").trim()
        if (q.isEmpty()) return null
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = runCatching {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        val labeled = apps.mapNotNull { ri ->
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull() ?: return@mapNotNull null
            label.lowercase() to ri.activityInfo.packageName
        }
        return labeled.firstOrNull { it.first == q }?.second
            ?: labeled.firstOrNull { it.first.startsWith(q) }?.second
            ?: labeled.firstOrNull { it.first.contains(q) }?.second
            ?: labeled.firstOrNull { it.second.lowercase().contains(q.replace(" ", "")) }?.second
    }

    // ------------------------------------------------------------------ notification actions
    /** Fires a PendingIntent from another app's notification (allowed to open its call screen). */
    fun sendAction(context: Context, action: Notification.Action): Boolean = runCatching {
        val pi = action.actionIntent ?: return false
        if (Build.VERSION.SDK_INT >= 34) {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(context, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ labels
    private const val WA_VOICE_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    private const val WA_VIDEO_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"

    val SEND_LABELS = listOf("Send", "Send message", "Send SMS", "পাঠান", "পাঠাও", "পাঠান বার্তা", "প্রেরণ")
    private val SMS_SEND_IDS = listOf(
        "com.google.android.apps.messaging:id/send_message_button_icon",
        "com.google.android.apps.messaging:id/send_message_button_container",
        "com.samsung.android.messaging:id/send_button1",
        "com.android.mms:id/send_button_sms"
    )
    val VOICE_CALL_LABELS = listOf(
        "Voice call", "Audio call", "Start a voice call", "Start voice call", "Call", "Phone call", "Voice Call",
        "ভয়েস কল", "অডিও কল", "কল"
    )
    val VIDEO_CALL_LABELS = listOf(
        "Video call", "Start a video call", "Start a video chat", "Video chat", "Video Call", "ভিডিও কল"
    )
    val CALL_CONFIRM_LABELS = listOf("Call", "CALL", "Start call", "Voice call", "Video call", "কল", "কল করুন")
    val SEARCH_LABELS = listOf("Search", "Search contacts", "Search Messenger", "Ask Meta AI or Search", "খুঁজুন", "সার্চ", "অনুসন্ধান")
}
