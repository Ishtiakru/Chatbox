package com.example.data.repository

import android.content.Context
import com.example.data.SparkAdminCommands
import com.example.data.SparkCommandProposal
import com.example.data.model.AdminAiUpdatePlan
import com.example.data.model.AdminAuthStatus
import com.example.data.model.ChatboxConstants
import com.example.data.model.CustomMenuIconName
import com.example.data.model.QeLocalReleaseArchive
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkCustomMenu
import com.example.data.model.SparkDelegation
import com.example.data.model.SparkDynamicConfig
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SparkAdminRepository(private val context: Context) {
    private val db = FirebaseProviders.firestore(context)

    fun observePublicWelcome(): Flow<String> = callbackFlow {
        val reg = db.collection("settings").document("sparkPublic")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, "settings/sparkPublic")
                    return@addSnapshotListener
                }
                val text = snap?.getString("welcome")?.trim().orEmpty()
                trySend(text)
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "settings/sparkPublic")
    }

    fun observeSparkAppearance(): Flow<SparkAppearance> {
        val themeFlow = callbackFlow {
            val reg = db.collection("settings").document("sparkTheme")
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, "settings/sparkTheme")
                        return@addSnapshotListener
                    }
                    val v = snap?.getString("value")
                    trySend(if (v == "lavender") "lavender" else "default")
                }
            awaitClose { reg.remove() }
        }
        val menuFlow = callbackFlow {
            val reg = db.collection("settings").document("sparkMenu")
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, "settings/sparkMenu")
                        return@addSnapshotListener
                    }
                    val v = snap?.getString("value")
                    trySend(if (v == "top") "top" else "bottom")
                }
            awaitClose { reg.remove() }
        }
        return combine(themeFlow, menuFlow) { theme, menu ->
            SparkAppearance(theme = theme, menu = menu)
        }.catch { e ->
            if (e is CancellationException) throw e
        }
    }

    fun observeSparkDynamicConfig(): Flow<SparkDynamicConfig> = callbackFlow {
        val reg = db.collection("settings").document("sparkConfig")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, "settings/sparkConfig")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(SparkDynamicConfig())
                    return@addSnapshotListener
                }
                val rawMenus = snap.get("customMenus") as? List<*> ?: emptyList<Any>()
                val menus = rawMenus.take(5).mapNotNull { entry ->
                    val map = entry as? Map<*, *> ?: return@mapNotNull null
                    sanitizeCustomMenu(
                        id = map["id"] as? String ?: "",
                        label = map["label"] as? String ?: "",
                        icon = map["icon"] as? String ?: "sparkles",
                        title = map["title"] as? String ?: "",
                        body = map["body"] as? String ?: ""
                    )
                }
                val title = snap.getString("appTitle")?.trim().orEmpty().take(40).ifEmpty { "Chatbox" }
                val announcement = snap.getString("homeAnnouncement")?.trim().orEmpty().take(240)
                trySend(SparkDynamicConfig(appTitle = title, homeAnnouncement = announcement, customMenus = menus))
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "settings/sparkConfig")
    }

    fun observeSparkAppUpdate(): Flow<SparkAppUpdate> = callbackFlow {
        val reg = db.collection("settings").document("sparkUpdate")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, "settings/sparkUpdate")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(SparkAppUpdate())
                } else {
                    trySend(
                        SparkAppUpdate(
                            active = snap.getBoolean("active") ?: false,
                            version = snap.getString("version").orEmpty(),
                            note = snap.getString("note").orEmpty(),
                            downloadUrl = snap.getString("downloadUrl").orEmpty()
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "settings/sparkUpdate")
    }

    fun observeSparkDelegations(): Flow<List<SparkDelegation>> = callbackFlow {
        val reg = db.collection("sparkDelegations")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "sparkDelegations")
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().map { d ->
                    SparkDelegation(
                        email = d.getString("email") ?: d.id,
                        uid = d.getString("uid").orEmpty(),
                        invitedBy = d.getString("invitedBy").orEmpty(),
                        status = d.getString("status") ?: "invited",
                        createdAt = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        updatedAt = d.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedBy { it.email }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "sparkDelegations")
    }

    suspend fun checkSparkAdminStatus(uid: String, rawEmail: String?): AdminAuthStatus {
        val email = rawEmail?.trim()?.lowercase().orEmpty()
        if (uid.isEmpty() || email.isEmpty()) return AdminAuthStatus.DENIED

        runCatching {
            val snap = db.collection("sparkAdmin").document("current").get().await()
            if (snap.exists() && snap.getString("uid") == uid) {
                return AdminAuthStatus.OWNER
            }
        }

        runCatching {
            val delSnap = db.collection("sparkDelegations").document(email).get().await()
            if (delSnap.exists()) {
                val status = delSnap.getString("status")
                if (status == "active" && delSnap.getString("uid") == uid) {
                    return AdminAuthStatus.DELEGATE
                }
                if (status == "invited") {
                    return AdminAuthStatus.INVITED_DELEGATE
                }
            }
        }

        if (email in ChatboxConstants.BOOTSTRAPPED_OWNER_EMAILS) {
            return runCatching {
                db.collection("sparkAdmin").document("current").set(
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "pendingEmail" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                AdminAuthStatus.OWNER
            }.getOrElse {
                AdminAuthStatus.UNCLAIMED_OWNER
            }
        }

        return AdminAuthStatus.DENIED
    }

    suspend fun claimSparkOwnership(uid: String, rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        return try {
            db.collection("sparkAdmin").document("current").set(
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "pendingEmail" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "sparkAdmin/current")
            Result.failure(e)
        }
    }

    suspend fun saveSparkDynamicConfig(uid: String, config: SparkDynamicConfig): Result<Unit> {
        val cleanMenus = config.customMenus.take(5).mapNotNull {
            sanitizeCustomMenu(it.id, it.label, it.icon, it.title, it.body)
        }
        val serializedMenus = cleanMenus.map { menu ->
            mapOf(
                "id" to menu.id,
                "label" to menu.label,
                "icon" to menu.icon,
                "title" to menu.title,
                "body" to menu.body
            )
        }
        return try {
            db.collection("settings").document("sparkConfig").set(
                mapOf(
                    "appTitle" to config.appTitle.trim().take(40).ifEmpty { "Chatbox" },
                    "homeAnnouncement" to config.homeAnnouncement.trim().take(240),
                    "customMenus" to serializedMenus,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "settings/sparkConfig")
            Result.failure(e)
        }
    }

    suspend fun applySparkProposal(uid: String, proposal: SparkCommandProposal): Result<String> {
        return try {
            when (proposal) {
                is SparkCommandProposal.SetWelcome -> {
                    val welcome = proposal.value.trim().take(ChatboxConstants.MAX_WELCOME_LENGTH)
                    require(welcome.isNotEmpty()) { "Welcome message cannot be empty." }
                    db.collection("settings").document("sparkPublic").set(
                        mapOf(
                            "welcome" to welcome,
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success("Updated welcome banner to \"$welcome\".")
                }
                is SparkCommandProposal.SetTheme -> {
                    db.collection("settings").document("sparkTheme").set(
                        mapOf(
                            "value" to proposal.value,
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success("Home appearance updated to ${proposal.value}.")
                }
                is SparkCommandProposal.SetMenu -> {
                    db.collection("settings").document("sparkMenu").set(
                        mapOf(
                            "value" to proposal.value,
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success("Navigation menu position updated to ${proposal.value}.")
                }
                is SparkCommandProposal.SetAppUpdate -> {
                    val version = proposal.version.trim().take(ChatboxConstants.MAX_UPDATE_VERSION_LENGTH)
                    val note = proposal.note.trim().take(ChatboxConstants.MAX_UPDATE_NOTE_LENGTH)
                    val url = proposal.downloadUrl.trim().take(ChatboxConstants.MAX_UPDATE_URL_LENGTH)
                    if (proposal.active && version.isEmpty()) {
                        return Result.failure(IllegalArgumentException("Enter a target version."))
                    }
                    if (url.isNotEmpty() && !url.startsWith("https://")) {
                        return Result.failure(IllegalArgumentException("Download URL must start with https://"))
                    }
                    db.collection("settings").document("sparkUpdate").set(
                        mapOf(
                            "active" to proposal.active,
                            "version" to if (proposal.active) version else "",
                            "note" to if (proposal.active) note else "",
                            "downloadUrl" to if (proposal.active) url else "",
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success(
                        if (proposal.active) "Published green Update button for v$version."
                        else "Cleared the app Update button."
                    )
                }
                is SparkCommandProposal.SetAppTitle -> {
                    val title = proposal.value.trim().take(40)
                    require(title.isNotEmpty()) { "App title cannot be empty." }
                    val existingSnap = runCatching {
                        db.collection("settings").document("sparkConfig").get().await()
                    }.getOrNull()
                    val existingMenus = (existingSnap?.get("customMenus") as? List<*>).orEmpty()
                        .mapNotNull { entry ->
                            val map = entry as? Map<*, *> ?: return@mapNotNull null
                            sanitizeCustomMenu(
                                id = map["id"] as? String ?: "",
                                label = map["label"] as? String ?: "",
                                icon = map["icon"] as? String ?: "sparkles",
                                title = map["title"] as? String ?: "",
                                body = map["body"] as? String ?: ""
                            )
                        }
                    val existingAnnouncement = existingSnap?.getString("homeAnnouncement").orEmpty()
                    saveSparkDynamicConfig(
                        uid,
                        SparkDynamicConfig(
                            appTitle = title,
                            homeAnnouncement = existingAnnouncement,
                            customMenus = existingMenus
                        )
                    ).getOrThrow()
                    Result.success("App title updated to \"$title\".")
                }
                is SparkCommandProposal.SetAnnouncement -> {
                    val announcement = proposal.value.trim().take(240)
                    val existingSnap = runCatching {
                        db.collection("settings").document("sparkConfig").get().await()
                    }.getOrNull()
                    val existingMenus = (existingSnap?.get("customMenus") as? List<*>).orEmpty()
                        .mapNotNull { entry ->
                            val map = entry as? Map<*, *> ?: return@mapNotNull null
                            sanitizeCustomMenu(
                                id = map["id"] as? String ?: "",
                                label = map["label"] as? String ?: "",
                                icon = map["icon"] as? String ?: "sparkles",
                                title = map["title"] as? String ?: "",
                                body = map["body"] as? String ?: ""
                            )
                        }
                    val existingTitle = existingSnap?.getString("appTitle")?.ifBlank { "Chatbox" } ?: "Chatbox"
                    saveSparkDynamicConfig(
                        uid,
                        SparkDynamicConfig(
                            appTitle = existingTitle,
                            homeAnnouncement = announcement,
                            customMenus = existingMenus
                        )
                    ).getOrThrow()
                    Result.success(
                        if (announcement.isEmpty()) "Cleared home announcement."
                        else "Updated home announcement."
                    )
                }
                is SparkCommandProposal.RemoveMenu -> {
                    val target = proposal.idOrLabel.trim().lowercase()
                    val existingSnap = runCatching {
                        db.collection("settings").document("sparkConfig").get().await()
                    }.getOrNull()
                    val existingMenus = (existingSnap?.get("customMenus") as? List<*>).orEmpty()
                        .mapNotNull { entry ->
                            val map = entry as? Map<*, *> ?: return@mapNotNull null
                            sanitizeCustomMenu(
                                id = map["id"] as? String ?: "",
                                label = map["label"] as? String ?: "",
                                icon = map["icon"] as? String ?: "sparkles",
                                title = map["title"] as? String ?: "",
                                body = map["body"] as? String ?: ""
                            )
                        }
                    val nextMenus = existingMenus.filterNot {
                        it.id.equals(target, ignoreCase = true) ||
                            it.label.equals(target, ignoreCase = true)
                    }
                    if (nextMenus.size == existingMenus.size) {
                        return Result.failure(IllegalArgumentException("No custom menu matched \"$target\"."))
                    }
                    val existingTitle = existingSnap?.getString("appTitle")?.ifBlank { "Chatbox" } ?: "Chatbox"
                    val existingAnnouncement = existingSnap?.getString("homeAnnouncement").orEmpty()
                    saveSparkDynamicConfig(
                        uid,
                        SparkDynamicConfig(
                            appTitle = existingTitle,
                            homeAnnouncement = existingAnnouncement,
                            customMenus = nextMenus
                        )
                    ).getOrThrow()
                    Result.success("Removed custom menu \"$target\".")
                }
                SparkCommandProposal.Unsupported ->
                    Result.failure(IllegalArgumentException("Unsupported command proposal."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "settings")
            Result.failure(e)
        }
    }

    suspend fun applyAdminAiUpdatePlan(
        uid: String,
        currentConfig: SparkDynamicConfig,
        plan: AdminAiUpdatePlan
    ): Result<String> {
        plan.theme?.takeIf { it == "default" || it == "lavender" }?.let { t ->
            applySparkProposal(uid, SparkCommandProposal.SetTheme(t))
                .onFailure { return Result.failure(it) }
        }
        plan.menuPosition?.takeIf { it == "top" || it == "bottom" }?.let { m ->
            applySparkProposal(uid, SparkCommandProposal.SetMenu(m))
                .onFailure { return Result.failure(it) }
        }
        plan.welcomeBanner?.trim()?.takeIf { it.isNotEmpty() }?.let { w ->
            applySparkProposal(uid, SparkCommandProposal.SetWelcome(w))
                .onFailure { return Result.failure(it) }
        }
        plan.appUpdate?.let { upd ->
            applySparkProposal(
                uid,
                SparkCommandProposal.SetAppUpdate(
                    active = upd.active,
                    version = upd.version.ifBlank { "1.0.1" },
                    note = upd.note,
                    downloadUrl = upd.downloadUrl
                )
            ).onFailure { return Result.failure(it) }
        }

        var updatedMenus = currentConfig.customMenus.toMutableList()
        var configChanged = false

        plan.removeMenuId?.let { remId ->
            val before = updatedMenus.size
            updatedMenus.removeAll { it.id == remId }
            if (updatedMenus.size != before) configChanged = true
        }

        plan.addOrUpdateMenu?.let { candidate ->
            val clean = sanitizeCustomMenu(
                candidate.id,
                candidate.label,
                candidate.icon,
                candidate.title,
                candidate.body
            )
            if (clean != null) {
                val idx = updatedMenus.indexOfFirst { it.id == clean.id }
                if (idx >= 0) {
                    updatedMenus[idx] = clean
                } else {
                    if (updatedMenus.size >= 5) {
                        updatedMenus = updatedMenus.drop(1).toMutableList()
                    }
                    updatedMenus.add(clean)
                }
                configChanged = true
            }
        }

        val nextTitle = plan.appTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
            ?: currentConfig.appTitle
        val nextAnnouncement = plan.homeAnnouncement?.trim()?.take(240)
            ?: currentConfig.homeAnnouncement

        if (configChanged ||
            nextTitle != currentConfig.appTitle ||
            nextAnnouncement != currentConfig.homeAnnouncement
        ) {
            saveSparkDynamicConfig(
                uid,
                SparkDynamicConfig(
                    appTitle = nextTitle,
                    homeAnnouncement = nextAnnouncement,
                    customMenus = updatedMenus
                )
            ).onFailure { return Result.failure(it) }
        }

        return Result.success(plan.summary.ifBlank { "App updated successfully!" })
    }

    suspend fun inviteSparkDelegate(ownerUid: String, rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        if (!SparkAdminCommands.isValidSparkTarget(email)) {
            return Result.failure(IllegalArgumentException("Enter a valid Gmail address (e.g. partner@gmail.com)."))
        }
        return try {
            val ref = db.collection("sparkDelegations").document(email)
            val existing = ref.get().await()
            if (existing.exists() && existing.getString("status") == "revoked") {
                ref.update(
                    mapOf(
                        "status" to "invited",
                        "uid" to "",
                        "invitedBy" to ownerUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "email" to email,
                        "uid" to "",
                        "invitedBy" to ownerUid,
                        "status" to "invited",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "sparkDelegations/$email")
            Result.failure(e)
        }
    }

    suspend fun revokeSparkDelegate(rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        return try {
            db.collection("sparkDelegations").document(email).update(
                mapOf(
                    "status" to "revoked",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sparkDelegations/$email")
            Result.failure(e)
        }
    }

    suspend fun acceptSparkDelegation(uid: String, rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        return try {
            db.collection("sparkDelegations").document(email).update(
                mapOf(
                    "status" to "active",
                    "uid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sparkDelegations/$email")
            Result.failure(e)
        }
    }

    /**
     * Packages the current release metadata, changelog, and GitHub APK release reference
     * into a single versioned ZIP inside `qe_releases/`, deleting any older ZIP first so
     * only the newest release package remains on device.
     */
    suspend fun packageQeReleaseZip(
        version: String,
        note: String,
        dynamicConfig: SparkDynamicConfig,
        appearance: SparkAppearance
    ): Result<Pair<QeLocalReleaseArchive, File>> = withContext(Dispatchers.IO) {
        try {
            val cleanVer = version.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifEmpty { ChatboxConstants.APP_VERSION }
            val dir = File(context.filesDir, "qe_releases").apply { mkdirs() }
            dir.listFiles()?.filter { it.extension.equals("zip", ignoreCase = true) }
                ?.forEach { it.delete() }

            val filename = "chatbox-qe-v$cleanVer.zip"
            val zipFile = File(dir, filename)
            val githubUrl = "${ChatboxConstants.GITHUB_RELEASES_BASE_URL}/tag/v$cleanVer"

            val manifestJson = JSONObject().apply {
                put("appName", dynamicConfig.appTitle)
                put("packageName", context.packageName)
                put("version", version.trim())
                put("note", note.trim())
                put("theme", appearance.theme)
                put("menuPosition", appearance.menu)
                put("customMenuCount", dynamicConfig.customMenus.size)
                put("apkReleaseUrl", githubUrl)
            }.toString(2)

            val releaseNotesMd = """
                # Chatbox Release v${version.trim()}

                ## Release Notes
                ${note.trim().ifEmpty { "General improvements and updates." }}

                ## Signed APK Release
                Tag: v$cleanVer
                Download URL: $githubUrl
            """.trimIndent()

            val menusArray = JSONArray().apply {
                dynamicConfig.customMenus.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("id", m.id)
                            put("label", m.label)
                            put("icon", m.icon)
                            put("title", m.title)
                            put("body", m.body)
                        }
                    )
                }
            }
            val configSnapshot = JSONObject().apply {
                put("appTitle", dynamicConfig.appTitle)
                put("homeAnnouncement", dynamicConfig.homeAnnouncement)
                put("theme", appearance.theme)
                put("menu", appearance.menu)
                put("customMenus", menusArray)
            }.toString(2)

            val entries = listOf(
                "release-manifest.json" to manifestJson,
                "RELEASE_NOTES.md" to releaseNotesMd,
                "live-config-snapshot.json" to configSnapshot
            )

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                entries.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }

            Result.success(
                QeLocalReleaseArchive(
                    version = version.trim(),
                    filename = filename,
                    sizeBytes = zipFile.length(),
                    fileCount = entries.size,
                    note = note.trim(),
                    downloadUrl = githubUrl
                ) to zipFile
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentQeZipInfo(): QeLocalReleaseArchive? {
        val dir = File(context.filesDir, "qe_releases")
        val latest = dir.listFiles()?.firstOrNull { it.extension.equals("zip", ignoreCase = true) }
            ?: return null
        val ver = latest.nameWithoutExtension.removePrefix("chatbox-qe-v")
        val count = runCatching { ZipFile(latest).use { it.size() } }.getOrDefault(3)
        return QeLocalReleaseArchive(
            version = ver,
            filename = latest.name,
            sizeBytes = latest.length(),
            fileCount = count,
            note = "Stored release bundle (previous ZIPs auto-cleared)",
            downloadUrl = "${ChatboxConstants.GITHUB_RELEASES_BASE_URL}/tag/v$ver"
        )
    }

    private fun sanitizeCustomMenu(
        id: String,
        label: String,
        icon: String,
        title: String,
        body: String
    ): SparkCustomMenu? {
        val cleanId = id.lowercase().replace(Regex("[^a-z0-9_-]"), "").take(32)
        val cleanLabel = label.trim().take(24)
        val cleanTitle = title.trim().ifEmpty { cleanLabel }.take(80)
        val cleanBody = body.trim().take(1000)
        val cleanIcon = CustomMenuIconName.fromKey(icon).key
        if (cleanId.isEmpty() || cleanLabel.isEmpty() || cleanTitle.isEmpty() || cleanBody.isEmpty()) {
            return null
        }
        return SparkCustomMenu(
            id = cleanId,
            label = cleanLabel,
            icon = cleanIcon,
            title = cleanTitle,
            body = cleanBody
        )
    }
}
