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
    fun `storage path encodes path separators`(@TempDir tmp: Path) {
        val target = tmp.resolve("docs/sub/foo.md").also {
            it.parent.createDirectories()
            it.writeText("x")
        }
        val service = FileReviewService.standalone(repoRoot = tmp)
        val review = service.openOrCreate(target)
        review.loadOrInit()

        val expected = tmp.resolve(".review-duet/files/docs%2Fsub%2Ffoo.md.json")
        assertTrue(expected.exists(), "expected $expected to exist")
    }

    @Test
    fun `paths containing percent sign are escaped`(@TempDir tmp: Path) {
        val service = FileReviewService.standalone(repoRoot = tmp)
        val encoded = FileReviewService.encodeRelativePath("a%b/c.md")
        assertEquals("a%25b%2Fc.md", encoded)
    }

    @Test
    fun `target outside repo root throws`(@TempDir tmp: Path) {
        val outside = tmp.parent.resolve("outside.md").apply { writeText("x") }
        val service = FileReviewService.standalone(repoRoot = tmp)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            service.openOrCreate(outside)
        }
    }

    @Test
    fun `repo root cache reuses resolved root across siblings`(@TempDir tmp: Path) {
        val a = tmp.resolve("docs/a.md").also { it.parent.createDirectories(); it.writeText("x") }
        val b = tmp.resolve("docs/b.md").also { it.writeText("x") }
        val service = FileReviewService.standalone(repoRoot = tmp)

        service.openOrCreate(a).loadOrInit()
        service.openOrCreate(b).loadOrInit()

        // Both should resolve to the same root; we don't have a direct hook to assert the cache hit,
        // so just confirm both reviews end up under .review-duet/files/ as expected:
        assertTrue(tmp.resolve(".review-duet/files/docs%2Fa.md.json").exists())
        assertTrue(tmp.resolve(".review-duet/files/docs%2Fb.md.json").exists())
    }
}
