package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReviewDataTest {

    @Test
    fun `should parse valid status`() {
        assertEquals(CommentStatus.OPEN, CommentStatus.fromString("open"))
        assertEquals(CommentStatus.PENDING_USER, CommentStatus.fromString("pending-user"))
        assertEquals(CommentStatus.FIXED, CommentStatus.fromString("fixed"))
    }

    @Test
    fun `should serialize status to json value`() {
        assertEquals("open", CommentStatus.OPEN.jsonValue)
        assertEquals("pending-user", CommentStatus.PENDING_USER.jsonValue)
    }

    @Test
    fun `should validate thread entry author`() {
        val entry = ThreadEntry(
            author = "user",
            text = "Test comment",
            at = "2024-01-23T10:00:00Z"
        )
        assertTrue(entry.isUserComment)
        assertFalse(entry.isAgentComment)
    }
}
