package com.codereview.local.services

import com.codereview.local.model.*
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class ReviewService(private val projectRoot: Path) {

    private val reviewDir: Path = projectRoot.resolve(".review")
    private val commentsFile: Path = reviewDir.resolve("comments.json")
    private val serializer = ReviewDataSerializer()
    private val gitService = GitService(projectRoot)

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

    fun initializeReview(baseCommit: String) {
        val data = ReviewData(
            version = 1,
            baseCommit = baseCommit,
            comments = mutableListOf()
        )
        saveReviewData(data)
    }

    fun addComment(file: String, line: Int, text: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")

        // Store current HEAD - the commit the user is looking at when adding the comment
        val currentCommit = gitService.getCurrentCommitSha() ?: "HEAD"

        val comment = Comment(
            id = data.getNextCommentId(),
            file = file,
            line = line,
            commit = currentCommit,
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

    fun isFileReviewed(filePath: String): Boolean {
        val data = loadReviewData() ?: return false
        return data.isFileReviewed(filePath)
    }

    fun markFileReviewed(filePath: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        data.markFileReviewed(filePath)
        saveReviewData(data)
    }

    fun unmarkFileReviewed(filePath: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        data.unmarkFileReviewed(filePath)
        saveReviewData(data)
    }

    fun toggleFileReviewed(filePath: String): Boolean {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val isNowReviewed = if (data.isFileReviewed(filePath)) {
            data.unmarkFileReviewed(filePath)
            false
        } else {
            data.markFileReviewed(filePath)
            true
        }
        saveReviewData(data)
        return isNowReviewed
    }

    fun getReviewedFilesCount(): Int {
        val data = loadReviewData() ?: return 0
        return data.reviewedFiles.size
    }

    /**
     * Accept all reviewed changes and advance the baseline to current HEAD.
     * This completes the current review phase and prepares for the next batch of changes.
     */
    fun acceptChanges() {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val currentHead = gitService.getCurrentCommitSha() ?: throw IllegalStateException("Cannot get current commit")

        // Move baseline to current HEAD and clear reviewed files
        val updatedData = data.copy(
            baseCommit = currentHead,
            reviewedFiles = mutableSetOf()
        )

        saveReviewData(updatedData)
        cachedData = null
    }

    /**
     * Check if there are any changes to review (between baseCommit and HEAD)
     */
    fun hasChangesToReview(): Boolean {
        val data = loadReviewData() ?: return false
        val currentHead = gitService.getCurrentCommitSha() ?: return false
        if (data.baseCommit == currentHead) return false

        val changedFiles = gitService.getChangedFilePaths(data.baseCommit, currentHead)
        return changedFiles.isNotEmpty()
    }
}
