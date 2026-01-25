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

    @Test
    fun `should get review tags sorted`() {
        // Create commits and tags
        tempDir.resolve("test.txt").toFile().writeText("v1")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "c1").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "tag", "review-r0").directory(tempDir.toFile()).start().waitFor()

        tempDir.resolve("test.txt").toFile().writeText("v2")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "c2").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "tag", "review-r1").directory(tempDir.toFile()).start().waitFor()

        // Also create a non-review tag
        ProcessBuilder("git", "tag", "v1.0.0").directory(tempDir.toFile()).start().waitFor()

        val tags = gitService.getReviewTags()

        assertEquals(listOf("review-r0", "review-r1"), tags)
    }

    @Test
    fun `should get changed files between refs`() {
        // Initial commit
        tempDir.resolve("existing.txt").toFile().writeText("original")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "c1").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "tag", "review-r0").directory(tempDir.toFile()).start().waitFor()

        // Make changes
        tempDir.resolve("existing.txt").toFile().writeText("modified")
        tempDir.resolve("new.txt").toFile().writeText("new file")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "c2").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "tag", "review-r1").directory(tempDir.toFile()).start().waitFor()

        val changes = gitService.getChangedFiles("review-r0", "review-r1")

        assertEquals(2, changes.size)
        assertTrue(changes.any { it.path == "existing.txt" && it.changeType == com.codereview.local.model.ChangeType.MODIFIED })
        assertTrue(changes.any { it.path == "new.txt" && it.changeType == com.codereview.local.model.ChangeType.ADDED })
    }

    @Test
    fun `should get recent commits`() {
        // Create multiple commits
        tempDir.resolve("test.txt").toFile().writeText("v1")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "First commit").directory(tempDir.toFile()).start().waitFor()

        tempDir.resolve("test.txt").toFile().writeText("v2")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "Second commit").directory(tempDir.toFile()).start().waitFor()

        tempDir.resolve("test.txt").toFile().writeText("v3")
        ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "Third commit").directory(tempDir.toFile()).start().waitFor()

        val commits = gitService.getRecentCommits(10)

        assertEquals(3, commits.size)
        assertEquals("Third commit", commits[0].message)
        assertEquals("Second commit", commits[1].message)
        assertEquals("First commit", commits[2].message)
        assertTrue(commits[0].sha.length == 40)
        assertTrue(commits[0].shortSha.length == 7)
    }
}
