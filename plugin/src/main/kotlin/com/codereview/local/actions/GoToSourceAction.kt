package com.codereview.local.actions

import com.codereview.local.diff.DiffCommentExtension
import com.codereview.local.ui.ChangesPanel
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Action to navigate from diff view to the source file.
 * Only available in diff views opened from the Review Duet sidebar.
 */
class GoToSourceAction : AnAction(
    "Go to Source",
    "Open the source file at current line",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val (review, filePath) = getReviewAndPath(e) ?: return

        // Get current line from caret
        val caretLine = editor.caretModel.logicalPosition.line

        // Resolve full path and open file
        val file = review.repoRoot.resolve(filePath).toFile()
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)

        if (virtualFile != null && virtualFile.exists()) {
            OpenFileDescriptor(project, virtualFile, caretLine, 0).navigate(true)
        }
    }

    override fun update(e: AnActionEvent) {
        // Only show in our diff views (where Review is passed)
        val hasContext = getReviewAndPath(e) != null
        e.presentation.isEnabledAndVisible = hasContext && e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    private fun getReviewAndPath(e: AnActionEvent): Pair<com.codereview.local.model.Review, String>? {
        // Get Review from DiffRequest (passed from ChangesPanel)
        val diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST) ?: return null
        val review = diffRequest.getUserData(ChangesPanel.REVIEW_KEY) ?: return null

        // Get file path from DiffContext (set by DiffCommentExtension)
        val diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
        val filePath = diffContext.getUserData(DiffCommentExtension.FILE_PATH_KEY) ?: return null

        return review to filePath
    }
}
