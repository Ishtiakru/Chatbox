package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

object ChatboxFirestoreProvider {
    private const val TAG = "ChatboxFirestore"
    private const val PERSISTENT_CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB

    @Volatile
    private var cachedInstance: FirebaseFirestore? = null

    @Volatile
    private var cachedDatabaseId: String? = null

    fun getInstance(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        return getInstance(databaseId)
    }

    fun getInstance(databaseId: String): FirebaseFirestore {
        val existing = cachedInstance
        if (existing != null && cachedDatabaseId == databaseId) {
            return existing
        }
        return synchronized(this) {
            val doubleChecked = cachedInstance
            if (doubleChecked != null && cachedDatabaseId == databaseId) {
                return@synchronized doubleChecked
            }
            val instance = FirebaseFirestore.getInstance(databaseId)
            try {
                instance.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(
                        persistentCacheSettings {
                            setSizeBytes(PERSISTENT_CACHE_SIZE_BYTES)
                        }
                    )
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Firestore settings already initialized; keeping existing persistence configuration.", e)
            }
            cachedDatabaseId = databaseId
            cachedInstance = instance
            instance
        }
    }
}
