package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

class CommentListPanel(
    private val project: Project,
    private val comments: List<Comment>,
    private val onCommentSelected: (Comment) -> Unit
) : JBScrollPane() {

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getListBackground()
    }

    init {
        comments.forEach { addCommentItem(it) }
        setViewportView(contentPanel)
        border = JBUI.Borders.empty()
    }

    private fun addCommentItem(comment: Comment) {
        val item = createCommentItem(comment)
        contentPanel.add(item)
        contentPanel.add(JSeparator().apply {
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        })
    }

    private fun createCommentItem(comment: Comment): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 12)
            isOpaque = true
            background = UIUtil.getListBackground()
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // Line 1: #ID [status] path:line + View link
        val line1 = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        val leftPart = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
        }

        leftPart.add(JBLabel("#${comment.id}").apply {
            foreground = JBColor.GRAY
            font = JBFont.regular()
        })

        leftPart.add(createStatusTag(comment.status))

        leftPart.add(JBLabel("${comment.file}:${comment.line}").apply {
            foreground = UIUtil.getListForeground()
            font = JBFont.regular()
        })

        // View link on the right
        val viewLink = createLink("View") {
            onCommentSelected(comment)
        }

        line1.add(leftPart, BorderLayout.WEST)
        line1.add(viewLink, BorderLayout.EAST)

        panel.add(line1)

        // Line 2: Comment text
        comment.firstUserMessage?.let { msg ->
            val line2 = JBLabel(msg).apply {
                foreground = JBColor.GRAY
                font = JBFont.regular()
                border = JBUI.Borders.emptyLeft(4)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel.add(Box.createVerticalStrut(4))
            panel.add(line2)
        }

        // Line 3: Commit link (if fixed)
        if (comment.status == CommentStatus.FIXED && comment.resolveCommit != null) {
            val line3 = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
            }

            line3.add(JBLabel("Fixed in:").apply {
                foreground = JBColor.GRAY
                font = JBFont.small()
            })

            line3.add(createLink(comment.resolveCommit!!) {
                showCommitInLog(project, comment.resolveCommit!!)
            })

            panel.add(Box.createVerticalStrut(4))
            panel.add(line3)
        }

        return panel
    }

    private fun createLink(text: String, onClick: () -> Unit): JComponent {
        return JBLabel(text).apply {
            foreground = JBColor.BLUE
            font = JBFont.small()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    onClick()
                }

                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    foreground = JBColor.BLUE.darker()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    foreground = JBColor.BLUE
                }
            })
        }
    }

    private fun createStatusTag(status: CommentStatus): JComponent {
        val color = getStatusColor(status)
        val bgColor = Color(color.red, color.green, color.blue, 30)

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

    fun refresh(newComments: List<Comment>) {
        contentPanel.removeAll()
        newComments.forEach { addCommentItem(it) }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    companion object {
        fun showCommitInLog(project: Project, commitSha: String) {
            // Open the Git tool window and let user find the commit
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Git")
            toolWindow?.activate {
                // Copy commit SHA to clipboard for easy search
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(commitSha), null)

                com.intellij.openapi.ui.Messages.showInfoMessage(
                    project,
                    "Commit SHA copied to clipboard: $commitSha\n\nUse Ctrl+F in the Git log to find it.",
                    "Find Commit"
                )
            }
        }
    }
}
