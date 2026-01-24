package com.codereview.local.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager

class ReviewFileWatcher(private val project: Project) : BulkFileListener {

    // Note: Subscription is handled via plugin.xml projectListeners - no init block needed

    override fun after(events: MutableList<out VFileEvent>) {
        val reviewFileChanged = events.any { event ->
            event.path?.contains(".review/comments.json") == true
        }

        if (reviewFileChanged) {
            refreshToolWindow()
        }
    }

    private fun refreshToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Code Review") ?: return

        val content = toolWindow.contentManager.getContent(0) ?: return
        val panel = content.component as? com.codereview.local.ui.ReviewPanel ?: return

        panel.refresh()
    }
}
