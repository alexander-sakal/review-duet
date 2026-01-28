package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import java.awt.*
import javax.swing.*

class CommentListPanel(
    private val project: Project,
    private val comments: List<Comment>,
    private val onCommentSelected: (Comment) -> Unit
) : JBScrollPane() {

    private val listModel = DefaultListModel<Comment>()
    private val commentList = JBList(listModel)

    init {
        comments.forEach { listModel.addElement(it) }

        commentList.cellRenderer = MultiLineCommentCellRenderer(project)
        commentList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        commentList.fixedCellHeight = -1 // Allow variable height
        commentList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = commentList.selectedValue
                if (selected != null) {
                    commentList.clearSelection()
                    onCommentSelected(selected)
                }
            }
        }

        setViewportView(commentList)
        border = JBUI.Borders.empty()
    }

    private class MultiLineCommentCellRenderer(private val project: Project) : ListCellRenderer<Comment> {

        override fun getListCellRendererComponent(
            list: JList<out Comment>,
            value: Comment,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(8, 12)
                isOpaque = true
                background = if (isSelected) {
                    UIUtil.getListSelectionBackground(true)
                } else {
                    UIUtil.getListBackground()
                }
            }

            val textColor = if (isSelected) {
                UIUtil.getListSelectionForeground(true)
            } else {
                UIUtil.getListForeground()
            }

            // Line 1: #ID [status] path:line
            val line1 = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
            }

            line1.add(JBLabel("#${value.id}").apply {
                foreground = if (isSelected) textColor else JBColor.GRAY
                font = JBFont.regular()
            })

            line1.add(createStatusTag(value.status, isSelected))

            line1.add(JBLabel("${value.file}:${value.line}").apply {
                foreground = textColor
                font = JBFont.regular()
            })

            panel.add(line1)

            // Line 2: Comment text
            value.firstUserMessage?.let { msg ->
                val line2 = JBLabel(msg).apply {
                    foreground = if (isSelected) textColor else JBColor.GRAY
                    font = JBFont.regular()
                    border = JBUI.Borders.emptyLeft(4)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                panel.add(Box.createVerticalStrut(4))
                panel.add(line2)
            }

            // Line 3: Commit link (if fixed)
            if (value.status == CommentStatus.FIXED && value.resolveCommit != null) {
                val line3 = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                line3.add(JBLabel("Fixed in:").apply {
                    foreground = if (isSelected) textColor else JBColor.GRAY
                    font = JBFont.small()
                })

                // Create a label styled as a link
                line3.add(JBLabel(value.resolveCommit).apply {
                    foreground = if (isSelected) textColor else JBColor.BLUE
                    font = JBFont.small()
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    toolTipText = "Click to view commit in Git log"
                })

                panel.add(Box.createVerticalStrut(4))
                panel.add(line3)
            }

            return panel
        }

        private fun createStatusTag(status: CommentStatus, isSelected: Boolean): JComponent {
            val color = getStatusColor(status)
            val bgColor = if (isSelected) {
                Color(color.red, color.green, color.blue, 60)
            } else {
                Color(color.red, color.green, color.blue, 30)
            }

            return object : JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    border = JBUI.Borders.empty(1, 6)
                    add(JBLabel(status.jsonValue).apply {
                        font = JBFont.small()
                        foreground = color
                    }, BorderLayout.CENTER)
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = bgColor
                    g2.fillRoundRect(0, 0, width, height, 8, 8)
                    g2.dispose()
                    super.paintComponent(g)
                }
            }
        }

        private fun getStatusColor(status: CommentStatus): Color {
            return when (status) {
                CommentStatus.OPEN -> Color(255, 193, 7)
                CommentStatus.PENDING_USER -> Color(33, 150, 243)
                CommentStatus.PENDING_AGENT -> Color(255, 152, 0)
                CommentStatus.FIXED -> Color(76, 175, 80)
                CommentStatus.RESOLVED -> Color(158, 158, 158)
                CommentStatus.WONTFIX -> Color(158, 158, 158)
            }
        }
    }

    fun refresh(newComments: List<Comment>) {
        listModel.clear()
        newComments.forEach { listModel.addElement(it) }
    }

    companion object {
        fun showCommitInLog(project: Project, commitSha: String) {
            val logManager = VcsProjectLog.getInstance(project)
            val logUi = logManager.mainLogUi ?: return

            // Focus the Git log tool window
            val toolWindow = ChangesViewContentManager.getToolWindowFor(project, "Log")
            toolWindow?.activate {
                logUi.jumpToCommit(commitSha.take(40), null)
            }
        }
    }
}
