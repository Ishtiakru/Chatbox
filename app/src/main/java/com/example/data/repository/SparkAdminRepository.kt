package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminAuthStatus
import com.example.data.model.AdminRole
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkCommandProposal
import com.example.data.model.SparkDelegation
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class AdminStatusResult(
    val status: AdminAuthStatus,
    val role: AdminRole
)

class SparkAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        private const val BOOTSTRAP_OWNER_EMAIL = "ishtiakahmedru@gmail.com"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    fun observeSparkWelcome(): Flow<String> {
        return db.collection("settings").document("sparkPublic")
            .snapshots()
            .map { snap ->
                if (snap.metadata.hasPendingWrites() || snap.metadata.isFromCache) {
                    return@map ""
                }
                val text = snap.getString("welcome")
                if (text != null && text.length <= 160) text else ""
            }
    }

    fun observeSparkAppearance(): Flow<SparkAppearance> {
        val themeFlow = db.collection("settings").document("sparkTheme")
            .snapshots()
            .map { snap ->
                if (snap.metadata.hasPendingWrites() || snap.metadata.isFromCache) null
                else snap.getString("value").takeIf { it == "default" || it == "lavender" } ?: "default"
            }
        val menuFlow = db.collection("settings").document("sparkMenu")
            .snapshots()
            .map { snap ->
                if (snap.metadata.hasPendingWrites() || snap.metadata.isFromCache) null
                else snap.getString("value").takeIf { it == "bottom" || it == "top" } ?: "bottom"
            }
        return combine(themeFlow, menuFlow) { theme, menu ->
            SparkAppearance(
                theme = theme ?: "default",
                menu = menu ?: "bottom"
            )
        }
    }

    fun observeSparkAppUpdate(): Flow<SparkAppUpdate> {
        return db.collection("settings").document("sparkUpdate")
            .snapshots()
            .map { snap ->
                if (!snap.exists() || snap.metadata.hasPendingWrites() || snap.metadata.isFromCache) {
                    return@map SparkAppUpdate()
                }
                val active = snap.getBoolean("active") == true
                val version = snap.getString("version")?.trim().orEmpty().take(32)
                val note = snap.getString("note")?.trim().orEmpty().take(240)
                val downloadUrl = snap.getString("downloadUrl")?.trim().orEmpty().take(512)
                val safeUrl = if (downloadUrl.startsWith("https://")) downloadUrl else ""
                SparkAppUpdate(
                    active = active && version.isNotEmpty(),
                    version = version,
                    note = note,
                    downloadUrl = safeUrl
                )
            }
    }

    suspend fun checkAdminStatus(uid: String, rawEmail: String?): Result<AdminStatusResult> {
        val email = rawEmail?.trim()?.lowercase().orEmpty()
        if (uid.isBlank() || email.isBlank()) {
            return Result.failure(IllegalStateException("Sign in with Google."))
        }
        val adminRef = db.collection("sparkAdmin").document("current")
        return try {
            try {
                val snap = adminRef.get(Source.SERVER).await()
                if (snap.exists() && snap.getString("uid") == uid) {
                    return Result.success(AdminStatusResult(AdminAuthStatus.AUTHORIZED, AdminRole.OWNER))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            }

            if (email == BOOTSTRAP_OWNER_EMAIL) {
                try {
                    adminRef.set(
                        mapOf(
                            "uid" to uid,
                            "email" to email,
                            "pendingEmail" to "",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                }

                try {
                    val snap = adminRef.get(Source.SERVER).await()
                    if (snap.exists() && snap.getString("uid") == uid) {
                        return Result.success(AdminStatusResult(AdminAuthStatus.AUTHORIZED, AdminRole.OWNER))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                }
            }

            if (SparkAdminCommands.validSparkTarget(email)) {
                val delRef = db.collection("sparkDelegations").document(email)
                try {
                    val snap = delRef.get(Source.SERVER).await()
                    if (snap.exists()) {
                        val status = snap.getString("status")
                        val delUid = snap.getString("uid")
                        if (status == "invited") {
                            return Result.success(AdminStatusResult(AdminAuthStatus.INVITED, AdminRole.DELEGATE))
                        }
                        if (status == "active" && delUid == uid) {
                            return Result.success(AdminStatusResult(AdminAuthStatus.AUTHORIZED, AdminRole.DELEGATE))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                }
            }

            Result.success(AdminStatusResult(AdminAuthStatus.FORBIDDEN, AdminRole.NONE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "sparkAdmin/current")
            Result.failure(e)
        }
    }

    suspend fun listDelegations(): Result<List<SparkDelegation>> {
        val path = "sparkDelegations"
        return try {
            requireUserId()
            val snap = db.collection("sparkDelegations").limit(100).get(Source.SERVER).await()
            val items = snap.documents.mapNotNull { doc ->
                doc.toObject(SparkDelegation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun applyProposal(uid: String, proposal: SparkCommandProposal): Result<Unit> {
        val path = when (proposal) {
            is SparkCommandProposal.SetWelcome -> "settings/sparkPublic"
            is SparkCommandProposal.SetTheme -> "settings/sparkTheme"
            is SparkCommandProposal.SetMenu -> "settings/sparkMenu"
            is SparkCommandProposal.SetAppUpdate -> "settings/sparkUpdate"
            SparkCommandProposal.Unsupported -> "settings"
        }
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            when (proposal) {
                is SparkCommandProposal.SetWelcome -> {
                    require(proposal.value.isNotEmpty() && proposal.value.length <= 160)
                    db.collection("settings").document("sparkPublic")
                        .set(
                            mapOf(
                                "welcome" to proposal.value,
                                "updatedBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
                is SparkCommandProposal.SetTheme -> {
                    require(proposal.value in listOf("default", "lavender"))
                    db.collection("settings").document("sparkTheme")
                        .set(
                            mapOf(
                                "value" to proposal.value,
                                "updatedBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
                is SparkCommandProposal.SetMenu -> {
                    require(proposal.value in listOf("bottom", "top"))
                    db.collection("settings").document("sparkMenu")
                        .set(
                            mapOf(
                                "value" to proposal.value,
                                "updatedBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
                is SparkCommandProposal.SetAppUpdate -> {
                    require(proposal.version.length <= 32)
                    require(!proposal.active || proposal.version.isNotEmpty())
                    require(proposal.note.length <= 240)
                    require(proposal.downloadUrl.length <= 512)
                    require(proposal.downloadUrl.isEmpty() || proposal.downloadUrl.startsWith("https://")) {
                        "Update download URL must start with https://"
                    }
                    db.collection("settings").document("sparkUpdate")
                        .set(
                            mapOf(
                                "active" to proposal.active,
                                "version" to proposal.version,
                                "note" to proposal.note,
                                "downloadUrl" to proposal.downloadUrl,
                                "updatedBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
                SparkCommandProposal.Unsupported -> {
                    return Result.failure(IllegalArgumentException("Unsupported Spark Admin command."))
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun inviteDelegate(uid: String, rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        val path = "sparkDelegations/$email"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            require(SparkAdminCommands.validSparkTarget(email)) { "Enter a valid Gmail address." }
            val ref = db.collection("sparkDelegations").document(email)
            db.runTransaction { tx ->
                val existing = tx.get(ref)
                if (!existing.exists()) {
                    tx.set(
                        ref,
                        mapOf(
                            "email" to email,
                            "uid" to "",
                            "invitedBy" to uid,
                            "status" to "invited",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else if (existing.getString("status") == "revoked") {
                    tx.update(
                        ref,
                        mapOf(
                            "uid" to "",
                            "invitedBy" to uid,
                            "status" to "invited",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    throw IllegalStateException("The invitation or delegation already exists.")
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeDelegate(rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        val path = "sparkDelegations/$email"
        return try {
            requireUserId()
            require(SparkAdminCommands.validSparkTarget(email)) { "Invalid Gmail address." }
            db.collection("sparkDelegations").document(email)
                .update(
                    mapOf(
                        "status" to "revoked",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptDelegation(uid: String, rawEmail: String?): Result<Unit> {
        val email = rawEmail?.trim()?.lowercase().orEmpty()
        val path = "sparkDelegations/$email"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            require(SparkAdminCommands.validSparkTarget(email)) { "Valid Gmail required." }
            db.collection("sparkDelegations").document(email)
                .update(
                    mapOf(
                        "uid" to uid,
                        "status" to "active",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
