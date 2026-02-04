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
        // Initialize git repo so GitService can detect branch
        ProcessBuilder("git", "init")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.email", "test@test.com")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.name", "Test")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        // Create initial commit so HEAD exists
        tempDir.resolve(".gitkeep").writeText("")
        ProcessBuilder("git", "add", ".")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "init")
            .directory(tempDir.toFile())
            .start()
            .waitFor()

        service = ReviewService(tempDir)
    }

    @Test
    fun `should return false when no review exists`() {
        assertFalse(service.hasActiveReview())
    }

    @Test
    fun `should return true when review file exists`() {
        val reviewDir = tempDir.resolve(".review-duet")
        reviewDir.createDirectories()
        // Branch is "main" or "master" depending on git version
        val branch = getBranch()
        reviewDir.resolve("$branch.json").writeText("""
            {"version": 1, "baseCommit": "abc1234", "comments": []}
        """.trimIndent())

        assertTrue(service.hasActiveReview())
    }

    @Test
    fun `should load review data`() {
        val reviewDir = tempDir.resolve(".review-duet")
        reviewDir.createDirectories()
        val branch = getBranch()
        reviewDir.resolve("$branch.json").writeText("""
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

    private fun getBranch(): String {
        val process = ProcessBuilder("git", "branch", "--show-current")
            .directory(tempDir.toFile())
            .start()
        return process.inputStream.bufferedReader().readText().trim().ifEmpty { "master" }
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
