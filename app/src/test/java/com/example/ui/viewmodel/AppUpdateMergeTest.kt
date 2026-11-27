package com.example.ui.viewmodel

import com.example.agent.GitHubRelease
import com.example.data.model.SparkAppUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateMergeTest {
    private fun rel(tag: String) = GitHubRelease(tag, "2026-11-27T00:00:00Z", emptyMap())

    @Test
    fun newGithubReleaseLightsTheButtonForEveryoneWithoutAdminStep() {
        val u = mergeAppUpdate(SparkAppUpdate(), rel("v1.0.0.128"), installedBuild = 125)
        assertTrue(u.active)
        assertEquals("1.0.0.128", u.version)
        assertTrue(u.note.isNotBlank())
    }

    @Test
    fun sameOrOlderReleaseMeansUpToDate() {
        assertFalse(mergeAppUpdate(SparkAppUpdate(), rel("v1.0.0.128"), installedBuild = 128).active)
        assertFalse(mergeAppUpdate(SparkAppUpdate(), null, installedBuild = 128).active)
    }

    @Test
    fun staleAdminNoticeIsIgnored() {
        val stale = SparkAppUpdate(active = true, version = "1.0.1", note = "old")
        assertFalse(mergeAppUpdate(stale, rel("v1.0.0.128"), installedBuild = 128).active)
    }

    @Test
    fun adminNoteIsKeptWhenItMatchesTheRelease() {
        val admin = SparkAppUpdate(active = true, version = "1.0.0.130", note = "কল ঠিক করা হয়েছে")
        val u = mergeAppUpdate(admin, rel("v1.0.0.130"), installedBuild = 128)
        assertEquals("কল ঠিক করা হয়েছে", u.note)
        assertEquals("1.0.0.130", u.version)
    }

    @Test
    fun adminCanAnnounceABuildBeforeGithubReportsIt() {
        val admin = SparkAppUpdate(active = true, version = "v1.0.0.131")
        assertTrue(mergeAppUpdate(admin, rel("v1.0.0.128"), installedBuild = 128).active)
    }
}
