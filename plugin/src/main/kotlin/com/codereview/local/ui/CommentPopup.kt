package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.model.ThreadEntry
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class CommentPopup(
    private val comment: Comment,
    private val onStatusChange: (CommentStatus) -> Unit
) : DialogWrapper(true) {

    private val resolveAction = object : DialogWrapperAction("Resolve") {
        override fun doAction(e: java.awt.event.ActionEvent?) {
            onStatusChange(CommentStatus.RESOLVED)
            close(OK_EXIT_CODE)
        }
    }

    init {
        title = "Comment #${comment.id}"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
        }

        // Header: file:line + status tag
        val headerPanel = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            border = JBUI.Borders.empty(0, 0, 8, 0)

            add(JBLabel("${comment.file}:${comment.line}").apply {
                font = JBFont.regular().deriveFont(Font.BOLD)
            }, BorderLayout.WEST)

            add(createStatusTag(), BorderLayout.EAST)
        }
        panel.add(headerPanel)
        panel.add(createSeparator())

        // Thread messages
        for (entry in comment.thread) {
            panel.add(createThreadEntryPanel(entry))
            panel.add(createSeparator())
        }

        // Action buttons at bottom
        val actionsPanel = createActionsPanel()
        if (actionsPanel.componentCount > 0) {
            panel.add(actionsPanel)
        }

        // Wrap in scroll pane for long threads
        val scrollPane = JBScrollPane(panel).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(500, minOf(400, panel.preferredSize.height + 50))
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        return scrollPane
    }

    private fun createStatusTag(): JComponent {
        val color = getStatusColor(comment.status)
        val bgColor = Color(color.red, color.green, color.blue, 30)

        return object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                border = JBUI.Borders.empty(2, 8)
                add(JBLabel(comment.status.jsonValue).apply {
                    font = JBFont.small()
                    foreground = color
                }, BorderLayout.CENTER)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = bgColor
                g2.fillRoundRect(0, 0, width, height, 10, 10)
                g2.dispose()
                super.paintComponent(g)
            }
        }
    }

    private fun createThreadEntryPanel(entry: ThreadEntry): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(10, 0)

            // Header: author + timestamp
            val authorColor = if (entry.isUserComment) {
                JBColor(0x2196F3, 0x6BAFFF)  // Blue
            } else {
                JBColor(0x4CAF50, 0x6A9F6A)  // Green
            }

            val authorLabel = JBLabel(if (entry.isUserComment) "You" else "Claude").apply {
                foreground = authorColor
                font = JBFont.regular().deriveFont(Font.BOLD)
            }

            val timeLabel = JBLabel(formatTimestamp(entry.at)).apply {
                foreground = JBColor.GRAY
                font = JBFont.small()
            }

            val headerRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(authorLabel, BorderLayout.WEST)
                add(timeLabel, BorderLayout.EAST)
            }

            // Message text
            val textLabel = JTextArea(entry.text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                font = JBFont.regular()
                foreground = UIUtil.getLabelForeground()
                border = JBUI.Borders.emptyTop(6)
            }

            add(headerRow, BorderLayout.NORTH)
            add(textLabel, BorderLayout.CENTER)
        }
    }

    private fun createSeparator(): JComponent {
        return JSeparator().apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }
    }

    private fun createActionsPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(8, 0, 0, 0)

            // Won't Fix only for open comments
            if (comment.status == CommentStatus.OPEN) {
                add(JButton("Won't Fix").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.WONTFIX)
                        close(OK_EXIT_CODE)
                    }
                })
            }
        }
    }

    private fun getStatusColor(status: CommentStatus): Color {
        return when (status) {
            CommentStatus.OPEN -> Color(255, 193, 7)
            CommentStatus.FIXED -> Color(76, 175, 80)
            CommentStatus.RESOLVED -> Color(158, 158, 158)
            CommentStatus.WONTFIX -> Color(158, 158, 158)
        }
    }

    private fun formatTimestamp(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            isoString
        }
    }

    override fun createActions(): Array<Action> {
        // Show Resolve button only for non-resolved comments
        return if (comment.status != CommentStatus.RESOLVED && comment.status != CommentStatus.WONTFIX) {
            arrayOf(resolveAction, okAction)
        } else {
            arrayOf(okAction)
        }
    }
}
