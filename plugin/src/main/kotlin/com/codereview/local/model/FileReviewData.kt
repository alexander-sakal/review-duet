package com.codereview.local.model

enum class FileCommentStatus(val jsonValue: String) {
    OPEN("open"),
    FIXED("fixed"),
    OUTDATED("outdated");

    companion object {
        fun fromString(value: String): FileCommentStatus =
            entries.find { it.jsonValue == value }
                ?: throw IllegalArgumentException("Unknown file comment status: $value")
    }
}

data class FileComment(
    val id: Int,
    val line: Int,
    val lineText: String,
    val contextBefore: List<String>,
    val contextAfter: List<String>,
    val text: String,
    var status: FileCommentStatus,
    val createdAt: String
)

data class FileReviewData(
    val version: Int,
    val mode: String,
    val filePath: String,
    val fileHashAtStart: String,
    var lastSeenHash: String,
    val createdAt: String,
    val comments: MutableList<FileComment>
) {
    fun getComment(id: Int): FileComment? = comments.find { it.id == id }
    fun getNextCommentId(): Int = (comments.maxOfOrNull { it.id } ?: 0) + 1
}
