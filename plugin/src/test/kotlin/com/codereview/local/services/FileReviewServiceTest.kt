package com.codereview.local.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import kotlin.io.path.*

class FileReviewServiceTest {

    @Test
    fun `openOrCreate creates a fresh review`(@TempDir tmp: Path) {
        val target = tmp.resolve("docs/foo.md").also {
            it.parent.createDirectories()
            it.writeText("hello\nworld")
        }
        val service = FileReviewService.standalone(repoRoot = tmp)

        val review = service.openOrCreate(target)
        val data = review.loadOrInit()

        assertEquals("file", data.mode)
        assertEquals(target.toString(), data.filePath)
        assertTrue(data.fileHashAtStart.isNotEmpty())
        assertEquals(0, data.comments.size)
    }

    @Test
    fun `openOrCreate twice returns equivalent stored review`(@TempDir tmp: Path) {
        val target = tmp.resolve("a.md").apply { writeText("x") }
        val service = FileReviewService.standalone(repoRoot = tmp)

        val first = service.openOrCreate(target).loadOrInit()
        val second = service.openOrCreate(target).loadOrInit()

        assertEquals(first.createdAt, second.createdAt)
    }

    @Test
    fun `storage path mirrors the source directory layout`(@TempDir tmp: Path) {
        val target = tmp.resolve("docs/sub/foo.md").also {
            it.parent.createDirectories()
            it.writeText("x")
        }
        val service = FileReviewService.standalone(repoRoot = tmp)
        val review = service.openOrCreate(target)
        review.loadOrInit()

        val expected = tmp.resolve(".review-duet/files/docs/sub/foo.md.json")
        assertTrue(expected.exists(), "expected $expected to exist")
    }

    @Test
    fun `target outside repo root throws`(@TempDir tmp: Path) {
        val outside = tmp.parent.resolve("outside.md").apply { writeText("x") }
        val service = FileReviewService.standalone(repoRoot = tmp)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            service.openOrCreate(outside)
        }
    }
}
