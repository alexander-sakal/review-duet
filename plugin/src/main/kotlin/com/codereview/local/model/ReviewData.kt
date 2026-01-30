package com.codereview.local.model

enum class CommentStatus(val jsonValue: String) {
    OPEN("open"),
    FIXED("fixed"),
    RESOLVED("resolved");

    companion object {
        fun fromString(value: String): CommentStatus {
            return entries.find { it.jsonValue == value }
                ?: throw IllegalArgumentException("Unknown status: $value")
        }
    }
}

data class ThreadEntry(
    val author: String,
    val text: String,
    val at: String
) {
    val isUserComment: Boolean get() = author == "user"
    val isAgentComment: Boolean get() = author == "agent"
}

data class Comment(
    val id: Int,
    val file: String,
    val line: Int,
    val commit: String,
    var status: CommentStatus,
    var resolveCommit: String?,
    val thread: MutableList<ThreadEntry>
) {
    val firstUserMessage: String?
        get() = thread.firstOrNull { it.isUserComment }?.text

    val lastAgentMessage: String?
        get() = thread.lastOrNull { it.isAgentComment }?.text
}

data class ReviewData(
    val version: Int,
    val baseCommit: String,
    val comments: MutableList<Comment>,
    val reviewedFiles: MutableSet<String> = mutableSetOf()
) {
    fun getComment(id: Int): Comment? = comments.find { it.id == id }

    fun getNextCommentId(): Int = (comments.maxOfOrNull { it.id } ?: 0) + 1

    fun getCommentsByStatus(status: CommentStatus): List<Comment> =
        comments.filter { it.status == status }

    fun isFileReviewed(filePath: String): Boolean = reviewedFiles.contains(filePath)

    fun markFileReviewed(filePath: String) {
        reviewedFiles.add(filePath)
    }

    fun unmarkFileReviewed(filePath: String) {
        reviewedFiles.remove(filePath)
    }

    fun clearReviewedFiles() {
        reviewedFiles.clear()
    }
}

enum class ChangeType(val symbol: String) {
    ADDED("A"),
    MODIFIED("M"),
    DELETED("D");

    companion object {
        fun fromGitStatus(status: String): ChangeType = when (status) {
            "A" -> ADDED
            "M" -> MODIFIED
            "D" -> DELETED
            else -> MODIFIED
        }
    }
}

data class ChangedFile(
    val path: String,
    val changeType: ChangeType
)
