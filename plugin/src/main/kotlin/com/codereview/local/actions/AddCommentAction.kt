package com.codereview.local.actions

import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

class AddCommentAction : AnAction("Add Review Comment", "Add a review comment at current line", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Find the repo that contains this file
        val (repoRoot, relativePath) = GitService.getRelativePath(project, virtualFile.path) ?: run {
            Messages.showWarningDialog(
                project,
                "File is not in a known git repository.",
                "No Repository Found"
            )
            return
        }

        val reviewService = ReviewService(repoRoot)

        // Check if there's an active review
        if (!reviewService.hasActiveReview()) {
            Messages.showWarningDialog(
                project,
                "No active review. Start feature development first from the Code Review panel.",
                "No Active Review"
            )
            return
        }

        // Get current line (1-based)
        val caretModel = editor.caretModel
        val line = caretModel.logicalPosition.line + 1

        // Show dialog to enter comment
        val dialog = AddCommentDialog(project, relativePath, line)
        if (dialog.showAndGet()) {
            val commentText = dialog.getCommentText()
            if (commentText.isNotBlank()) {
                reviewService.addComment(relativePath, line, commentText)
                Messages.showInfoMessage(
                    project,
                    "Comment added to $relativePath:$line",
                    "Comment Added"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        e.presentation.isEnabledAndVisible = project != null && editor != null && virtualFile != null
    }
}