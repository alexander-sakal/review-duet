package com.codereview.local.actions

import com.codereview.local.diff.DiffCommentExtension
import com.codereview.local.services.GitService
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
        val (repoRoot, filePath) = getPathsFromEvent(e) ?: return

        val reviewService = ReviewService(repoRoot)
        reviewService.toggleFileReviewed(filePath)
    }

    override fun update(e: AnActionEvent) {
        val paths = getPathsFromEvent(e)

        if (paths == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val (repoRoot, filePath) = paths
        val reviewService = ReviewService(repoRoot)

        if (!reviewService.hasActiveReview()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isReviewed = reviewService.isFileReviewed(filePath)
        e.presentation.isEnabledAndVisible = true
        e.presentation.icon = if (isReviewed) AllIcons.Actions.Checked else AllIcons.General.InspectionsEye
        e.presentation.text = if (isReviewed) "Reviewed" else "Mark Reviewed"
        e.presentation.description = if (isReviewed) "File is reviewed - click to unmark" else "Mark this file as reviewed"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getPathsFromEvent(e: AnActionEvent): Pair<Path, String>? {
        val project = e.project ?: return null

        // Try to get from diff context (set by our DiffCommentExtension)
        val diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT)
        if (diffContext != null) {
            val filePath = diffContext.getUserData(DiffCommentExtension.FILE_PATH_KEY)
            val basePath = diffContext.getUserData(DiffCommentExtension.BASE_PATH_KEY)
            if (filePath != null && basePath != null) {
                return Path.of(basePath) to filePath
            }
        }

        // Try to get from diff request title
        val diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST)
        if (diffRequest != null) {
            val title = diffRequest.title
            if (title != null) {
                val relativePath = extractFilePath(title)
                // Find the repo that contains this file
                val repos = GitService.discoverRepos(project)
                for (repo in repos) {
                    if (repo.resolve(relativePath).toFile().exists()) {
                        return repo to relativePath
                    }
                }
            }
        }

        // Try to get from virtual file
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null) {
            return GitService.getRelativePath(project, virtualFile.path)
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
