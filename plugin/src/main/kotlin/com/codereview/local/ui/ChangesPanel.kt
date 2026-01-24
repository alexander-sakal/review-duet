package com.codereview.local.ui

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import com.codereview.local.services.GitService
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

class ChangesPanel(
    private val project: Project,
    private val gitService: GitService
) : JPanel(BorderLayout()) {

    private val fromCombo = JComboBox<String>()
    private val toCombo = JComboBox<String>()
    private val listModel = DefaultListModel<ChangedFile>()
    private val fileList = JBList(listModel)

    init {
        border = JBUI.Borders.empty(5)

        // Header with dropdowns
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("From: "))
            add(fromCombo)
            add(Box.createHorizontalStrut(10))
            add(JLabel("To: "))
            add(toCombo)
            add(Box.createHorizontalGlue())
        }

        // Setup combos
        fromCombo.addActionListener { refreshFileList() }
        toCombo.addActionListener { refreshFileList() }

        // Setup file list
        fileList.cellRenderer = ChangedFileCellRenderer()
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                fileList.selectedValue?.let { openDiff(it) }
            }
        }

        add(headerPanel, BorderLayout.NORTH)
        add(JBScrollPane(fileList), BorderLayout.CENTER)

        refreshTags()
    }

    fun refresh() {
        refreshTags()
    }

    private fun refreshTags() {
        val tags = gitService.getReviewTags()
        val selectedFrom = fromCombo.selectedItem as? String
        val selectedTo = toCombo.selectedItem as? String

        fromCombo.removeAllItems()
        toCombo.removeAllItems()

        tags.forEach { tag ->
            fromCombo.addItem(tag)
            toCombo.addItem(tag)
        }

        // Restore selection or set defaults
        if (tags.isNotEmpty()) {
            if (selectedFrom != null && tags.contains(selectedFrom)) {
                fromCombo.selectedItem = selectedFrom
            } else if (tags.size >= 2) {
                fromCombo.selectedIndex = tags.size - 2
            }

            if (selectedTo != null && tags.contains(selectedTo)) {
                toCombo.selectedItem = selectedTo
            } else {
                toCombo.selectedIndex = tags.size - 1
            }
        }

        refreshFileList()
    }

    private fun refreshFileList() {
        val fromRef = fromCombo.selectedItem as? String ?: return
        val toRef = toCombo.selectedItem as? String ?: return

        if (fromRef == toRef) {
            listModel.clear()
            return
        }

        val changes = gitService.getChangedFiles(fromRef, toRef)
        listModel.clear()
        changes.forEach { listModel.addElement(it) }
    }

    private fun openDiff(file: ChangedFile) {
        val fromRef = fromCombo.selectedItem as? String ?: return
        val toRef = toCombo.selectedItem as? String ?: return

        val fromContent = when (file.changeType) {
            ChangeType.ADDED -> ""
            else -> gitService.getFileAtRef(fromRef, file.path) ?: ""
        }

        val toContent = when (file.changeType) {
            ChangeType.DELETED -> ""
            else -> gitService.getFileAtRef(toRef, file.path) ?: ""
        }

        val contentFactory = DiffContentFactory.getInstance()
        val request = SimpleDiffRequest(
            "${file.path} ($fromRef → $toRef)",
            contentFactory.create(fromContent),
            contentFactory.create(toContent),
            fromRef,
            toRef
        )

        DiffManager.getInstance().showDiff(project, request)
    }

    private class ChangedFileCellRenderer : ColoredListCellRenderer<ChangedFile>() {
        override fun customizeCellRenderer(
            list: JList<out ChangedFile>,
            value: ChangedFile,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val (color, attrs) = when (value.changeType) {
                ChangeType.ADDED -> JBColor.GREEN to SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                ChangeType.MODIFIED -> JBColor.BLUE to SimpleTextAttributes.REGULAR_ATTRIBUTES
                ChangeType.DELETED -> JBColor.RED to SimpleTextAttributes.REGULAR_ATTRIBUTES
            }

            append("${value.changeType.symbol}  ", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color))
            append(value.path, attrs)
        }
    }
}
