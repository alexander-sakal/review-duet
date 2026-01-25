package com.codereview.local.diff

import com.intellij.diff.DiffRequestPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * File editor wrapping a diff panel for review files.
 */
class ReviewDiffFileEditor(
    private val project: Project,
    private val file: ReviewDiffVirtualFile,
    private val diffPanel: DiffRequestPanel
) : UserDataHolderBase(), FileEditor {

    override fun getComponent(): JComponent = diffPanel.getComponent()

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel.getPreferredFocusedComponent()

    override fun getName(): String = "Review Diff"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        Disposer.dispose(diffPanel)
    }

    override fun getFile() = file
}
