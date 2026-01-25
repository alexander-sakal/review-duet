package com.codereview.local.services

import com.codereview.local.model.*
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class ReviewService(private val projectRoot: Path) {

    private val reviewDir: Path = projectRoot.resolve(".review")
    private val commentsFile: Path = reviewDir.resolve("comments.json")
    private val serializer = ReviewDataSerializer()

    private var cachedData: ReviewData? = null

    fun hasActiveReview(): Boolean = commentsFile.exists()

    fun loadReviewData(): ReviewData? {
        if (!hasActiveReview()) return null

        val json = commentsFile.readText()
        cachedData = serializer.deserialize(json)
        return cachedData
    }

    fun saveReviewData(data: ReviewData) {
        if (!reviewDir.exists()) {
            reviewDir.createDirectories()
        }
        commentsFile.writeText(serializer.serialize(data))
        cachedData = data
    }

    fun initializeReview(baseRef: String) {
        val data = ReviewData(
            version = 1,
            currentRound = baseRef,
            baseRef = baseRef,
            comments = mutableListOf()
        )
        saveReviewData(data)
    }

    fun addComment(file: String, line: Int, text: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")

        val comment = Comment(
            id = data.getNextCommentId(),
            file = file,
            line = line,
            ref = data.currentRound,
            status = CommentStatus.OPEN,
            resolveCommit = null,
            thread = mutableListOf(
                ThreadEntry(
                    author = "user",
                    text = text,
                    at = Instant.now().toString()
                )
            )
        )

        data.comments.add(comment)
        saveReviewData(data)
    }

    fun updateCommentStatus(commentId: Int, status: CommentStatus) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        comment.status = status
        saveReviewData(data)
    }

    fun updateCommentText(commentId: Int, newText: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        val lastEntry = comment.thread.lastOrNull()
        if (lastEntry != null) {
            val updatedEntry = lastEntry.copy(text = newText)
            comment.thread[comment.thread.lastIndex] = updatedEntry
            saveReviewData(data)
        }
    }

    fun addReply(commentId: Int, text: String, asAgent: Boolean = false) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        comment.thread.add(
            ThreadEntry(
                author = if (asAgent) "agent" else "user",
                text = text,
                at = Instant.now().toString()
            )
        )

        // Update status based on who replied
        if (asAgent && comment.status == CommentStatus.OPEN) {
            comment.status = CommentStatus.PENDING_USER
        } else if (!asAgent && comment.status == CommentStatus.PENDING_USER) {
            comment.status = CommentStatus.PENDING_AGENT
        }

        saveReviewData(data)
    }

    fun startNewRound(roundTag: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        data.currentRound = roundTag
        saveReviewData(data)
    }
}
