package com.codereview.local.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Editor provider for ReviewDiffVirtualFile.
 * Creates a diff editor showing changes since baseRef.
 */
class ReviewDiffEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is ReviewDiffVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val reviewFile = file as ReviewDiffVirtualFile
        val basePath = Path.of(project.basePath!!)

        val diffRequest = ReviewDiffRequestProducer.createDiffRequest(
            project, basePath, reviewFile.filePath, reviewFile.baseRef
        ) ?: SimpleDiffRequest(
            "Review: ${reviewFile.filePath}",
            DiffContentFactory.getInstance().createEmpty(),
            DiffContentFactory.getInstance().createEmpty(),
            reviewFile.baseRef,
            "Current"
        )

        // Create diff viewer using DiffManager
        val diffPanel = DiffManager.getInstance().createRequestPanel(project, {}, null)
        diffPanel.setRequest(diffRequest)

        return ReviewDiffFileEditor(project, reviewFile, diffPanel)
    }

    override fun getEditorTypeId(): String = "ReviewDiffEditor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
