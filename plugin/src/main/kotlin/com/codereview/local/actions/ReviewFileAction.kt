package com.codereview.local.actions

import com.codereview.local.diff.FileReviewEditorCommentExtension
import com.codereview.local.services.FileReviewService
import com.codereview.local.ui.FileReviewPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
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
        val service = FileReviewService.getInstance(project)

        // Detect whether this call creates a brand-new review entry (storage file absent).
        val storagePath = service.resolveStoragePath(target)
        val isNewReview = storagePath != null && !storagePath.toFile().exists()

        service.openOrCreate(target)

        // When the JSON is first created, the editor was already open so editorCreated never fired
        // for it. Sweep open editors and install the gutter affordance retroactively.
        if (isNewReview) {
            ApplicationManager.getApplication().invokeLater {
                val targetPath = target.toString()
                EditorFactory.getInstance().allEditors.forEach { editor ->
                    if (editor.editorKind != EditorKind.MAIN_EDITOR) return@forEach
                    val editorVfile = FileDocumentManager.getInstance().getFile(editor.document)
                        ?: return@forEach
                    if (editorVfile.path == targetPath) {
                        FileReviewEditorCommentExtension.install(editor, project)
                    }
                }
            }
        }

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
