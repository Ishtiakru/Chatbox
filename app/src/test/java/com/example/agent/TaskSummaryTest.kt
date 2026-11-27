package com.example.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSummaryTest {
    @Test
    fun showsSairasUnderstandingNotTheRawWrapper() {
        val stored = "Admin's exact words: seta to thik ache ata to korla\nSaira's understanding: Make the green update button work for regular users."
        assertEquals("Make the green update button work for regular users.", taskSummary(stored))
    }

    @Test
    fun fallsBackToTheAdminText() {
        assertEquals("add a help button", taskSummary("Admin's exact words: add a help button"))
        assertEquals("plain", taskSummary("plain"))
    }
}
