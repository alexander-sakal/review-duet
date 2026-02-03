package com.codereview.local.actions

import com.codereview.local.services.GitService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.file.Path

class GoToSourceAction : AnAction(
    "Go to Source",
    "Open the source file at current line",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        // Get current line from caret
        val caretLine = editor.caretModel.logicalPosition.line

        // Try to get file path from context - check file header or tab title
        val (repoRoot, relativePath) = getFilePathFromContext(e) ?: return

        // Find the virtual file
        val file = repoRoot.resolve(relativePath).toFile()
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

    private fun getFilePathFromContext(e: AnActionEvent): Pair<Path, String>? {
        val project = e.project ?: return null
        val repos = GitService.discoverRepos(project)
        if (repos.isEmpty()) return null

        // Try to get from the file header component or context
        val context = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)

        // Walk up the component tree to find the diff frame title
        var component = context
        while (component != null) {
            val name = component.name
            if (name != null && name.contains(".") && !name.contains("→")) {
                // Might be a file path
                val path = name.substringBefore(" (").trim()
                if (path.isNotEmpty()) {
                    // Try to find which repo contains this file
                    for (repo in repos) {
                        if (repo.resolve(path).toFile().exists()) {
                            return repo to path
                        }
                    }
                }
            }

            // Check if it's a JFrame with title
            if (component is java.awt.Frame) {
                val title = component.title
                val path = extractFilePath(title)
                if (path != null) {
                    // Try to find which repo contains this file
                    for (repo in repos) {
                        if (repo.resolve(path).toFile().exists()) {
                            return repo to path
                        }
                    }
                }
            }

            component = component.parent
        }

        // Try from virtual file if available
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null) {
            return GitService.getRelativePath(project, virtualFile.path)
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
