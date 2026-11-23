package com.example.saira

import kotlinx.serialization.Serializable

@Serializable
data class SairaDirectoryUser(
    val uid: String,
    val name: String,
    val shortId: String = "",
    val about: String = "",
    val isSelf: Boolean = false
)

object SairaUserDirectory {

    const val MAX_USERS = 40

    /**
     * Builds the local snapshot of Chatbox users for the auto-reply prompt.
     * The user's own entry comes first, blank names are dropped, duplicate uids are removed
     * and the list is capped at [MAX_USERS]. Phone numbers are intentionally never stored.
     */
    fun build(
        self: SairaDirectoryUser?,
        peers: List<SairaDirectoryUser>
    ): List<SairaDirectoryUser> {
        val candidates = ArrayList<SairaDirectoryUser>(peers.size + 1)
        self?.let { candidates.add(it) }
        candidates.addAll(peers)

        val seenUids = LinkedHashSet<String>()
        val result = ArrayList<SairaDirectoryUser>()
        for (candidate in candidates) {
            val uid = candidate.uid.trim()
            val name = candidate.name.trim()
            if (uid.isEmpty() || name.isEmpty()) continue
            if (!seenUids.add(uid)) continue
            result.add(
                candidate.copy(
                    uid = uid,
                    name = name,
                    shortId = candidate.shortId.trim(),
                    about = candidate.about.trim()
                )
            )
            if (result.size >= MAX_USERS) break
        }
        return result
    }
}
