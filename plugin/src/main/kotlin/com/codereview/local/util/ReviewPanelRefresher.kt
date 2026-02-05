package com.codereview.local.util

import com.codereview.local.ui.ReviewPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.SwingUtilities

/**
 * Utility to refresh the Review Panel from anywhere in the plugin.
 * Use this after making changes to review data (add/edit/delete comments, status changes).
 */
object ReviewPanelRefresher {

    /**
     * Refresh the Review Panel for the given project.
     * Safe to call from any thread - will dispatch to EDT if needed.
     */
    fun refresh(project: Project) {
        if (SwingUtilities.isEventDispatchThread()) {
            doRefresh(project)
        } else {
            SwingUtilities.invokeLater { doRefresh(project) }
        }
    }

    private fun doRefresh(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Review Duet") ?: return

        val content = toolWindow.contentManager.getContent(0) ?: return
        val panel = content.component as? ReviewPanel ?: return

        panel.refresh()
    }
}
