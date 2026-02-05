package com.codereview.local.model

import com.codereview.local.services.GitService
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

/**
 * Represents an active review session.
 * Encapsulates the repo root, review file path, and all review operations.
 * Pass this single object instead of separate repoRoot + reviewFilePath parameters.
 */
class Review(
    val repoRoot: Path,
    val filePath: Path
) {
    val gitService = GitService(repoRoot)
    private val reviewDuetDir: Path = repoRoot.resolve(".review-duet")
    private val serializer = ReviewDataSerializer()

    private var cachedData: ReviewData? = null

    private fun ensureGitignore() {
        val gitignore = repoRoot.resolve(".gitignore")
        val entry = ".review-duet/"
        var modified = false

        if (gitignore.exists()) {
            val content = gitignore.readText()
            if (!content.contains(entry)) {
                val newContent = if (content.endsWith("\n")) {
                    "$content$entry\n"
                } else {
                    "$content\n$entry\n"
                }
                gitignore.writeText(newContent)
                modified = true
            }
        } else {
            gitignore.writeText("$entry\n")
            modified = true
        }

        if (modified) {
            gitService.commitFile(".gitignore", "chore: add .review-duet to gitignore")
        }
    }

    fun hasActiveReview(): Boolean = filePath.exists()

    fun loadData(): ReviewData? {
        if (!hasActiveReview()) {
            cachedData = null
            return null
        }

        cachedData?.let { return it }

        val json = filePath.readText()
        cachedData = serializer.deserialize(json)
        return cachedData
    }

    /**
     * Force reload from disk, bypassing cache.
     * Use when external changes may have occurred.
     */
    fun reloadData(): ReviewData? {
        cachedData = null
        return loadData()
    }

    fun saveData(data: ReviewData) {
        if (!reviewDuetDir.exists()) {
            reviewDuetDir.createDirectories()
            ensureGitignore()
        }
        filePath.writeText(serializer.serialize(data))
        cachedData = data
    }

    fun initialize(baseCommit: String) {
        val data = ReviewData(
            version = 1,
            baseCommit = baseCommit,
            comments = mutableListOf()
        )
        saveData(data)
    }

    fun addComment(file: String, line: Int, text: String) {
        val data = loadData() ?: throw IllegalStateException("No active review")

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
        saveData(data)
    }

    fun updateCommentStatus(commentId: Int, status: CommentStatus) {
        val data = loadData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        comment.status = status
        saveData(data)
    }

    fun updateCommentText(commentId: Int, newText: String) {
        val data = loadData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        val lastEntry = comment.thread.lastOrNull()
        if (lastEntry != null) {
            val updatedEntry = lastEntry.copy(text = newText)
            comment.thread[comment.thread.lastIndex] = updatedEntry
            saveData(data)
        }
    }

    fun deleteComment(commentId: Int) {
        val data = loadData() ?: throw IllegalStateException("No active review")
        data.comments.removeIf { it.id == commentId }
        saveData(data)
    }

    fun isFileReviewed(filePath: String): Boolean {
        val data = loadData() ?: return false
        return data.isFileReviewed(filePath)
    }

    fun markFileReviewed(filePath: String) {
        val data = loadData() ?: throw IllegalStateException("No active review")
        data.markFileReviewed(filePath)
        saveData(data)
    }

    fun unmarkFileReviewed(filePath: String) {
        val data = loadData() ?: throw IllegalStateException("No active review")
        data.unmarkFileReviewed(filePath)
        saveData(data)
    }

    fun toggleFileReviewed(filePath: String): Boolean {
        val data = loadData() ?: throw IllegalStateException("No active review")
        val isNowReviewed = if (data.isFileReviewed(filePath)) {
            data.unmarkFileReviewed(filePath)
            false
        } else {
            data.markFileReviewed(filePath)
            true
        }
        saveData(data)
        return isNowReviewed
    }

    fun getReviewedFilesCount(): Int {
        val data = loadData() ?: return 0
        return data.reviewedFiles.size
    }

    /**
     * Accept all reviewed changes and advance the baseline to current HEAD.
     */
    fun acceptChanges() {
        val data = loadData() ?: throw IllegalStateException("No active review")
        val currentHead = gitService.getCurrentCommitSha() ?: throw IllegalStateException("Cannot get current commit")

        val updatedData = data.copy(
            baseCommit = currentHead,
            reviewedFiles = mutableSetOf()
        )

        saveData(updatedData)
        cachedData = null
    }

    /**
     * Check if there are any changes to review (between baseCommit and HEAD)
     */
    fun hasChangesToReview(): Boolean {
        val data = loadData() ?: return false
        val currentHead = gitService.getCurrentCommitSha() ?: return false
        if (data.baseCommit == currentHead) return false

        val changedFiles = gitService.getChangedFilePaths(data.baseCommit, currentHead)
        return changedFiles.isNotEmpty()
    }

    companion object {
        /**
         * Create a Review for the current branch in the given repo.
         */
        fun forCurrentBranch(repoRoot: Path): Review {
            val gitService = GitService(repoRoot)
            val branch = gitService.getCurrentBranch() ?: "main"
            val filePath = repoRoot.resolve(".review-duet").resolve("$branch.json")
            return Review(repoRoot, filePath)
        }
    }
}
