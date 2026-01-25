package com.codereview.local.actions

import com.codereview.local.diff.ReviewDiffVirtualFile
import com.codereview.local.services.ReviewService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import java.nio.file.Path

/**
 * Action to open the review diff view for the current file.
 */
class OpenReviewDiffAction : AnAction("Open Review Diff", "Open diff view for current file with review comments", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val reviewService = ReviewService(Path.of(basePath))
        val reviewData = reviewService.loadReviewData() ?: return

        // Get relative path
        val relativePath = vFile.path.removePrefix("$basePath/")

        // Create and open virtual file
        val reviewDiffFile = ReviewDiffVirtualFile(project, relativePath, reviewData.baseRef)
        FileEditorManager.getInstance(project).openFile(reviewDiffFile, true)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val basePath = project?.basePath
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val enabled = if (project != null && basePath != null && vFile != null) {
            val reviewService = ReviewService(Path.of(basePath))
            reviewService.hasActiveReview()
        } else {
            false
        }

        e.presentation.isEnabledAndVisible = enabled
    }
}
