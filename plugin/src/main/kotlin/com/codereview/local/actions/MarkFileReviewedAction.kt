package com.codereview.local.actions

import com.codereview.local.services.ReviewService
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import java.nio.file.Path

class MarkFileReviewedAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val filePath = getFilePath(e) ?: return

        val reviewService = ReviewService(Path.of(basePath))
        reviewService.toggleFileReviewed(filePath)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val basePath = project?.basePath
        val filePath = getFilePath(e)

        if (project == null || basePath == null || filePath == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val reviewService = ReviewService(Path.of(basePath))
        if (!reviewService.hasActiveReview()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isReviewed = reviewService.isFileReviewed(filePath)
        e.presentation.isEnabledAndVisible = true
        e.presentation.icon = if (isReviewed) AllIcons.Actions.Checked else AllIcons.Actions.CheckOut
        e.presentation.text = if (isReviewed) "Reviewed" else "Mark Reviewed"
        e.presentation.description = if (isReviewed) "File is reviewed - click to unmark" else "Mark this file as reviewed"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getFilePath(e: AnActionEvent): String? {
        val project = e.project ?: return null
        val basePath = project.basePath ?: return null

        // Try to get from diff request
        val diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST)
        if (diffRequest != null) {
            val title = diffRequest.title
            if (title != null) {
                return extractFilePath(title)
            }
        }

        // Try to get from virtual file
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null) {
            return virtualFile.path.removePrefix("$basePath/")
        }

        return null
    }

    private fun extractFilePath(title: String): String {
        return when {
            title.startsWith("Review:") -> title.removePrefix("Review:").trim()
            title.contains(" (") -> title.substringBefore(" (")
            else -> title
        }
    }
}
