package com.codereview.local.model

import com.codereview.local.services.CommentAnchorResolver
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.*

class FileReview(
    val targetFile: Path,
    val storageFile: Path
) {
    private val serializer = FileReviewDataSerializer()
    private val resolver = CommentAnchorResolver()

    fun loadOrInit(): FileReviewData {
        if (storageFile.exists()) {
            return serializer.deserialize(storageFile.readText())
        }
        val hash = computeHash()
        val data = FileReviewData(
            version = 1,
            mode = "file",
            filePath = targetFile.toString(),
            fileHashAtStart = hash,
            lastSeenHash = hash,
            createdAt = Instant.now().toString(),
            comments = mutableListOf()
        )
        save(data)
        return data
    }

    fun save(data: FileReviewData) {
        storageFile.parent?.createDirectories()
        storageFile.writeText(serializer.serialize(data))
    }

    /**
     * Compute current file hash and, if changed, re-anchor every comment.
     * Returns the (possibly updated) data and persists it on changes.
     */
    fun refresh(data: FileReviewData): FileReviewData {
        if (!targetFile.exists()) return data
        val currentHash = computeHash()
        if (currentHash == data.lastSeenHash) return data

        val currentLines = targetFile.readText().lines()
        val updated = resolver.resolveAll(data.comments, currentLines)
        data.comments.clear()
        data.comments.addAll(updated)
        data.lastSeenHash = currentHash
        save(data)
        return data
    }

    fun delete() {
        if (storageFile.exists()) storageFile.deleteExisting()
    }

    private fun computeHash(): String {
        val bytes = if (targetFile.exists()) targetFile.readBytes() else ByteArray(0)
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
