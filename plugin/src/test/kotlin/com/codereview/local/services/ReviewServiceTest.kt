package com.codereview.local.services

import com.codereview.local.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.io.path.readText

class ReviewServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: ReviewService

    @BeforeEach
    fun setup() {
        service = ReviewService(tempDir)
    }

    @Test
    fun `should return false when no review exists`() {
        assertFalse(service.hasActiveReview())
    }

    @Test
    fun `should return true when review file exists`() {
        val reviewDir = tempDir.resolve(".review")
        reviewDir.createDirectories()
        reviewDir.resolve("comments.json").writeText("""
            {"version": 1, "baseCommit": "abc1234", "comments": []}
        """.trimIndent())

        assertTrue(service.hasActiveReview())
    }

    @Test
    fun `should load review data`() {
        val reviewDir = tempDir.resolve(".review")
        reviewDir.createDirectories()
        reviewDir.resolve("comments.json").writeText("""
            {
                "version": 1,
                "baseCommit": "abc1234",
                "comments": [
                    {"id": 1, "file": "test.kt", "line": 10, "commit": "abc1234", "status": "open", "resolveCommit": null, "thread": []}
                ]
            }
        """.trimIndent())

        val data = service.loadReviewData()

        assertNotNull(data)
        assertEquals("abc1234", data?.baseCommit)
        assertEquals(1, data?.comments?.size)
    }

    @Test
    fun `should initialize new review`() {
        service.initializeReview("abc1234")

        val data = service.loadReviewData()
        assertNotNull(data)
        assertEquals("abc1234", data?.baseCommit)
        assertTrue(data?.comments?.isEmpty() == true)
    }

    @Test
    fun `should add comment`() {
        service.initializeReview("abc1234")

        service.addComment("src/Test.kt", 42, "Fix this bug")

        val data = service.loadReviewData()
        assertEquals(1, data?.comments?.size)
        assertEquals("src/Test.kt", data?.comments?.get(0)?.file)
        assertEquals(42, data?.comments?.get(0)?.line)
    }
}
