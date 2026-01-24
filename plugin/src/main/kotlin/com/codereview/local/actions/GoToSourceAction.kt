package com.codereview.local.actions

import com.intellij.diff.DiffDataKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class GoToSourceAction : AnAction(
    "Go to Source",
    "Open the source file at current line",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        // Get current line from caret
        val caretLine = editor.caretModel.logicalPosition.line

        // Try to get file path from diff request
        val diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST)
        val title = diffRequest?.title

        // Extract file path from title (format: "path/to/file.kt (review-r0 → review-r1)")
        val filePath = extractFilePath(title) ?: return

        // Find the virtual file
        val file = File(basePath, filePath)
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)

        if (virtualFile != null && virtualFile.exists()) {
            // Open file at the current line
            OpenFileDescriptor(project, virtualFile, caretLine, 0).navigate(true)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST)

        // Only enable in diff view context
        e.presentation.isEnabledAndVisible = project != null && editor != null && diffRequest != null
    }

    private fun extractFilePath(title: String?): String? {
        if (title == null) return null

        // Format: "path/to/file.kt (review-r0 → review-r1)"
        val path = title.substringBefore(" (").trim()
        if (path.isNotEmpty() && !path.contains("→") && path.contains(".")) {
            return path
        }

        return null
    }
}
