package com.codereview.local.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class AddCommentDialog(
    project: Project,
    private val filePath: String,
    private val line: Int
) : DialogWrapper(project) {

    private val textArea = JBTextArea().apply {
        rows = 5
        columns = 40
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        title = "Add Review Comment"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = Dimension(400, 200)
        }

        val locationLabel = JBLabel("$filePath:$line").apply {
            border = JBUI.Borders.emptyBottom(10)
        }

        val scrollPane = JBScrollPane(textArea)

        panel.add(locationLabel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = textArea

    fun getCommentText(): String = textArea.text.trim()
}