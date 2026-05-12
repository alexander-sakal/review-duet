package com.codereview.local.services

import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CommentAnchorResolverTest {

    private val resolver = CommentAnchorResolver()

    private fun comment(line: Int, lineText: String,
                        before: List<String> = emptyList(),
                        after: List<String> = emptyList()) = FileComment(
        id = 1, line = line, lineText = lineText,
        contextBefore = before, contextAfter = after,
        text = "comment", status = FileCommentStatus.OPEN, createdAt = "t"
    )

    @Test
    fun `unchanged file leaves comment at its original line`() {
        val current = listOf("a", "b", "## Auth", "c")
        val c = comment(line = 3, lineText = "## Auth")
        val result = resolver.resolve(c, current)
        assertEquals(3, result.line)
        assertEquals(FileCommentStatus.OPEN, result.status)
    }

    @Test
    fun `lineText found at a different line re-anchors and stays OPEN`() {
        val current = listOf("inserted", "a", "b", "## Auth", "c")
        val c = comment(line = 3, lineText = "## Auth")
        val result = resolver.resolve(c, current)
        assertEquals(4, result.line)
        assertEquals(FileCommentStatus.OPEN, result.status)
    }

    @Test
    fun `multiple matches disambiguated by contextBefore`() {
        val current = listOf("first", "header", "second", "header", "third")
        val c = comment(line = 2, lineText = "header", before = listOf("first"))
        val result = resolver.resolve(c, current)
        assertEquals(2, result.line)
        assertEquals(FileCommentStatus.OPEN, result.status)
    }

    @Test
    fun `multiple matches disambiguated by contextAfter`() {
        val current = listOf("header", "alpha", "header", "beta")
        val c = comment(line = 3, lineText = "header", after = listOf("beta"))
        val result = resolver.resolve(c, current)
        assertEquals(3, result.line)
        assertEquals(FileCommentStatus.OPEN, result.status)
    }

    @Test
    fun `no match marks comment OUTDATED and keeps original line`() {
        val current = listOf("a", "b", "c")
        val c = comment(line = 3, lineText = "## Auth")
        val result = resolver.resolve(c, current)
        assertEquals(3, result.line)
        assertEquals(FileCommentStatus.OUTDATED, result.status)
    }

    @Test
    fun `ambiguous matches with no resolving context mark OUTDATED`() {
        val current = listOf("dup", "dup", "dup")
        val c = comment(line = 2, lineText = "dup")
        val result = resolver.resolve(c, current)
        assertEquals(FileCommentStatus.OUTDATED, result.status)
    }

    @Test
    fun `resolveAll re-anchors multiple comments in one pass`() {
        val current = listOf("inserted", "a", "## Auth", "b", "## API")
        val comments = listOf(
            FileComment(1, 2, "## Auth", emptyList(), emptyList(), "x", FileCommentStatus.OPEN, "t"),
            FileComment(2, 4, "## API",  emptyList(), emptyList(), "y", FileCommentStatus.OPEN, "t")
        )
        val result = resolver.resolveAll(comments, current)
        assertEquals(3, result[0].line)
        assertEquals(5, result[1].line)
        assertEquals(FileCommentStatus.OPEN, result[0].status)
        assertEquals(FileCommentStatus.OPEN, result[1].status)
    }
}
