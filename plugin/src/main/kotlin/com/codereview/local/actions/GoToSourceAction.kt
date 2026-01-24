package com.codereview.local.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
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

        // Try to get file path from context - check file header or tab title
        val filePath = getFilePathFromContext(e, basePath) ?: return

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
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }

    private fun getFilePathFromContext(e: AnActionEvent, basePath: String): String? {
        // Try to get from the file header component or context
        val context = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)

        // Walk up the component tree to find the diff frame title
        var component = context
        while (component != null) {
            val name = component.name
            if (name != null && name.contains(".") && !name.contains("→")) {
                // Might be a file path
                val path = name.substringBefore(" (").trim()
                if (path.isNotEmpty() && File(basePath, path).exists()) {
                    return path
                }
            }

            // Check if it's a JFrame with title
            if (component is java.awt.Frame) {
                val title = component.title
                val path = extractFilePath(title)
                if (path != null && File(basePath, path).exists()) {
                    return path
                }
            }

            component = component.parent
        }

        // Try from virtual file if available
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null) {
            val path = virtualFile.path
            if (path.startsWith(basePath)) {
                return path.removePrefix(basePath).removePrefix("/")
            }
        }

        return null
    }

    private fun extractFilePath(title: String?): String? {
        if (title == null) return null

        // Format: "path/to/file.kt (review-r0 → review-r1)" or similar
        val path = title.substringBefore(" (").trim()
        if (path.isNotEmpty() && path.contains(".") && !path.contains("→")) {
            return path
        }

        return null
    }
}
