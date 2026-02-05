package com.codereview.local.actions

import com.codereview.local.diff.DiffCommentExtension
import com.codereview.local.model.Review
import com.codereview.local.ui.ChangesPanel
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to mark a file as reviewed in the diff view toolbar.
 * Only available in diff views opened from the Review Duet sidebar.
 */
class MarkFileReviewedAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val (review, filePath) = getReviewAndPath(e) ?: return
        review.toggleFileReviewed(filePath)
    }

    override fun update(e: AnActionEvent) {
        val data = getReviewAndPath(e)

        if (data == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val (review, filePath) = data

        if (!review.hasActiveReview()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isReviewed = review.isFileReviewed(filePath)
        e.presentation.isEnabledAndVisible = true
        e.presentation.icon = if (isReviewed) AllIcons.Actions.Checked else AllIcons.General.InspectionsEye
        e.presentation.text = if (isReviewed) "Reviewed" else "Mark Reviewed"
        e.presentation.description = if (isReviewed) "File is reviewed - click to unmark" else "Mark this file as reviewed"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getReviewAndPath(e: AnActionEvent): Pair<Review, String>? {
        // Get Review from DiffRequest (passed from ChangesPanel)
        val diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST) ?: return null
        val review = diffRequest.getUserData(ChangesPanel.REVIEW_KEY) ?: return null

        // Get file path from DiffContext (set by DiffCommentExtension)
        val diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
        val filePath = diffContext.getUserData(DiffCommentExtension.FILE_PATH_KEY) ?: return null

        return review to filePath
    }
}
