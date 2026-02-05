package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.model.Review
import com.intellij.openapi.project.Project
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
    private val review: Review,
    private val comments: List<Comment>,
    private val onCommentSelected: (Comment) -> Unit,
    private val onStatusChange: ((Comment, CommentStatus) -> Unit)? = null
) : JBScrollPane() {

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getListBackground()
    }

    init {
        comments.forEach { addCommentItem(it) }
        // Add vertical glue to push items to top
        contentPanel.add(Box.createVerticalGlue())
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
        val innerPanel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8, 12)
            isOpaque = true
            background = UIUtil.getListBackground()
        }

        // Wrapper to constrain height
        val panel = object : JPanel(BorderLayout()) {
            override fun getMaximumSize(): Dimension {
                val pref = preferredSize
                return Dimension(Int.MAX_VALUE, pref.height)
            }
        }.apply {
            isOpaque = false
            add(innerPanel, BorderLayout.CENTER)
        }

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        // Line 1: #ID [status] path:line
        val line1 = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
        }

        line1.add(JBLabel("#${comment.id}").apply {
            foreground = JBColor.GRAY
            font = JBFont.regular()
        })

        line1.add(createStatusTag(comment.status))

        line1.add(JBLabel("${comment.file}:${comment.line}").apply {
            foreground = UIUtil.getListForeground()
            font = JBFont.regular()
        })

        innerPanel.add(line1, gbc)

        // Line 2: Comment text (truncated preview)
        comment.firstUserMessage?.let { msg ->
            gbc.gridy++
            gbc.insets = JBUI.insets(4, 4, 0, 0)
            val preview = if (msg.length > 80) msg.take(80) + "..." else msg
            val line2 = JBLabel(preview).apply {
                foreground = JBColor.GRAY
                font = JBFont.regular()
            }
            innerPanel.add(line2, gbc)
        }

        // Line 3: Actions (View + Fixed in commit if applicable)
        gbc.gridy++
        gbc.insets = JBUI.insets(4, 4, 0, 0)
        val actionsLine = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }

        actionsLine.add(createLink("View") {
            onCommentSelected(comment)
        })

        if (comment.status == CommentStatus.FIXED && comment.resolveCommit != null) {
            actionsLine.add(Box.createHorizontalStrut(8))
            actionsLine.add(JBLabel("·").apply {
                foreground = JBColor.GRAY
            })
            actionsLine.add(Box.createHorizontalStrut(8))
            actionsLine.add(JBLabel("Fixed in: ").apply {
                foreground = JBColor.GRAY
                font = JBFont.small()
            })
            actionsLine.add(createLink(comment.resolveCommit!!) {
                // Show only changes in the fix commit itself (commit^ to commit)
                ChangesPanel.openDiffForSingleCommit(project, review, comment.resolveCommit!!, comment.file, comment.id)
            })

            // Add Resolve link
            if (onStatusChange != null) {
                actionsLine.add(Box.createHorizontalStrut(8))
                actionsLine.add(JBLabel("·").apply {
                    foreground = JBColor.GRAY
                })
                actionsLine.add(Box.createHorizontalStrut(8))
                actionsLine.add(createLink("Resolve") {
                    onStatusChange.invoke(comment, CommentStatus.RESOLVED)
                })
            }
        }

        innerPanel.add(actionsLine, gbc)

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
            CommentStatus.FIXED -> Color(76, 175, 80)
            CommentStatus.RESOLVED -> Color(158, 158, 158)
        }
    }

    fun refresh(newComments: List<Comment>) {
        contentPanel.removeAll()
        newComments.forEach { addCommentItem(it) }
        contentPanel.add(Box.createVerticalGlue())
        contentPanel.revalidate()
        contentPanel.repaint()
    }

}
