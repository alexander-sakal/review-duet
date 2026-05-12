package com.codereview.local.actions

import com.codereview.local.services.FileReviewService
import com.codereview.local.ui.FileReviewPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import java.nio.file.Path

class ReviewFileAction : AnAction() {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 1L * 1024 * 1024 // 1 MB
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isEligible(e.getData(CommonDataKeys.VIRTUAL_FILE))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vfile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!isEligible(vfile)) return

        val target = Path.of(vfile.path)
        FileReviewService.getInstance(project).openOrCreate(target)

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Review Duet Files") ?: return
        toolWindow.activate {
            val content: Content? = toolWindow.contentManager.contents.firstOrNull()
            (content?.component as? FileReviewPanel)?.openReview(target)
        }
    }

    private fun isEligible(vfile: VirtualFile?): Boolean {
        if (vfile == null || vfile.isDirectory) return false
        if (FileTypeRegistry.getInstance().getFileTypeByFile(vfile).isBinary) return false
        if (vfile.length > MAX_FILE_SIZE_BYTES) return false
        return true
    }
}
