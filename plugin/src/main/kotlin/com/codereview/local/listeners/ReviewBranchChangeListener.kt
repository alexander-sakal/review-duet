package com.codereview.local.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.openapi.wm.ToolWindowManager
import com.codereview.local.ui.ReviewPanel

class ReviewBranchChangeListener(private val project: Project) : BranchChangeListener {

    override fun branchWillChange(branchName: String) {
        // No action needed before change
    }

    override fun branchHasChanged(branchName: String) {
        // Refresh the review panel when branch changes
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Review")
        toolWindow?.contentManager?.contents?.forEach { content ->
            (content.component as? ReviewPanel)?.refresh()
        }
    }
}
