package com.codereview.local.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var gitService: GitService

    @BeforeEach
    fun setup() {
        gitService = GitService(tempDir)
        // Initialize git repo
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
    }

    @Test
    fun `should create tag`() {
        // Create a commit first
        tempDir.resolve("test.txt").toFile().writeText("test")
        ProcessBuilder("git", "add", ".")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "Initial commit")
            .directory(tempDir.toFile())
            .start()
            .waitFor()

        val result = gitService.createTag("review-r0")

        assertTrue(result)
        assertTrue(gitService.tagExists("review-r0"))
    }

    @Test
    fun `should check if tag exists`() {
        assertFalse(gitService.tagExists("nonexistent"))
    }

    @Test
    fun `should get current commit sha`() {
        tempDir.resolve("test.txt").toFile().writeText("test")
        ProcessBuilder("git", "add", ".")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "Initial commit")
            .directory(tempDir.toFile())
            .start()
            .waitFor()

        val sha = gitService.getCurrentCommitSha()

        assertNotNull(sha)
        assertTrue(sha!!.length >= 7)
    }
}
