package com.codereview.local.actions

import com.codereview.local.diff.ReviewDiffVirtualFile
import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * Action to open the review diff view for the current file.
 */
class OpenReviewDiffAction : AnAction("Open Review Diff", "Open diff view for current file with review comments", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Find the repo that contains this file
        val (repoRoot, relativePath) = GitService.getRelativePath(project, vFile.path) ?: return

        val reviewService = ReviewService(repoRoot)
        val reviewData = reviewService.loadReviewData() ?: return

        // Create and open virtual file
        val reviewDiffFile = ReviewDiffVirtualFile(project, relativePath, reviewData.baseCommit)
        FileEditorManager.getInstance(project).openFile(reviewDiffFile, true)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val enabled = if (project != null && vFile != null) {
            val pathInfo = GitService.getRelativePath(project, vFile.path)
            if (pathInfo != null) {
                val reviewService = ReviewService(pathInfo.first)
                reviewService.hasActiveReview()
            } else {
                false
            }
        } else {
            false
        }

        e.presentation.isEnabledAndVisible = enabled
    }
}
