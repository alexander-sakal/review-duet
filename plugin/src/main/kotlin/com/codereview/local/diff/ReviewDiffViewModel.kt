package com.codereview.local.diff

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.services.ReviewService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path

/**
 * View model for a review diff, providing comment data and actions.
 */
class ReviewDiffViewModel(
    private val project: Project,
    private val basePath: Path,
    val filePath: String
) {
    private val reviewService = ReviewService(basePath)

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _isLoading.value = true
        try {
            val data = reviewService.loadReviewData()
            _comments.value = data?.comments?.filter { it.file == filePath } ?: emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    fun addComment(line: Int, text: String) {
        reviewService.addComment(filePath, line, text)
        reload()
    }

    fun updateCommentStatus(commentId: Int, status: CommentStatus) {
        reviewService.updateCommentStatus(commentId, status)
        reload()
    }

    fun updateCommentText(commentId: Int, newText: String) {
        reviewService.updateCommentText(commentId, newText)
        reload()
    }

    fun getBaseRef(): String? {
        return reviewService.loadReviewData()?.baseRef
    }

    fun hasActiveReview(): Boolean {
        return reviewService.hasActiveReview()
    }
}
