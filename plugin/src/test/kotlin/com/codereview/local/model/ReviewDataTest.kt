package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReviewDataTest {

    @Test
    fun `should parse valid status`() {
        assertEquals(CommentStatus.OPEN, CommentStatus.fromString("open"))
        assertEquals(CommentStatus.FIXED, CommentStatus.fromString("fixed"))
        assertEquals(CommentStatus.RESOLVED, CommentStatus.fromString("resolved"))
    }

    @Test
    fun `should serialize status to json value`() {
        assertEquals("open", CommentStatus.OPEN.jsonValue)
        assertEquals("fixed", CommentStatus.FIXED.jsonValue)
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
