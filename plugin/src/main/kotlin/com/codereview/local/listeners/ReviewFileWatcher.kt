package com.codereview.local.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.codereview.local.ui.ReviewPanel

/**
 * Watches for changes to the active review file.
 *
 * This is primarily for detecting external changes made by the CLI (Claude Code).
 * Internal plugin changes use direct refresh via ReviewPanelRefresher.
 *
 * Only triggers refresh when the currently displayed review file changes,
 * not when other branch/repo review files change.
 */
class ReviewFileWatcher(private val project: Project) : BulkFileListener {

    // Note: Subscription is handled via plugin.xml projectListeners - no init block needed

    override fun after(events: MutableList<out VFileEvent>) {
        val panel = getReviewPanel() ?: return
        val activeFilePath = panel.getActiveReviewFilePath() ?: return

        // Only refresh if the active review file was changed
        val activeFileChanged = events.any { event ->
            event.path == activeFilePath
        }

        if (activeFileChanged) {
            panel.refresh()
        }
    }

    private fun getReviewPanel(): ReviewPanel? {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Code Review") ?: return null

        val content = toolWindow.contentManager.getContent(0) ?: return null
        return content.component as? ReviewPanel
    }
}
