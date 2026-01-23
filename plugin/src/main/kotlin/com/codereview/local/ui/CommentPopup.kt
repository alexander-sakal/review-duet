package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.model.ThreadEntry
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class CommentPopup(
    private val comment: Comment,
    private val onReply: (String) -> Unit,
    private val onStatusChange: (CommentStatus) -> Unit
) : DialogWrapper(true) {

    private val replyArea = JBTextArea(3, 40)

    init {
        title = "Comment #${comment.id}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 400)

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 10, 0)
            add(JBLabel("${comment.file}:${comment.line}"), BorderLayout.WEST)
            add(createStatusLabel(), BorderLayout.EAST)
        }

        // Thread
        val threadPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(5)

            for (entry in comment.thread) {
                add(createThreadEntryPanel(entry))
                add(Box.createVerticalStrut(10))
            }
        }

        val threadScroll = JBScrollPane(threadPanel).apply {
            border = BorderFactory.createTitledBorder("Thread")
        }

        // Reply input
        val replyPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Reply")
            replyArea.lineWrap = true
            replyArea.wrapStyleWord = true
            add(JBScrollPane(replyArea), BorderLayout.CENTER)
        }

        // Status buttons
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)

            if (comment.status == CommentStatus.FIXED) {
                add(JButton("Resolve").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.RESOLVED)
                        close(OK_EXIT_CODE)
                    }
                })
                add(Box.createHorizontalStrut(5))
                add(JButton("Reopen").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.OPEN)
                        close(OK_EXIT_CODE)
                    }
                })
            }

            if (comment.status == CommentStatus.OPEN || comment.status == CommentStatus.PENDING_AGENT) {
                add(JButton("Won't Fix").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.WONTFIX)
                        close(OK_EXIT_CODE)
                    }
                })
            }
        }

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(threadScroll, BorderLayout.CENTER)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(replyPanel, BorderLayout.CENTER)
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createStatusLabel(): JLabel {
        val color = when (comment.status) {
            CommentStatus.OPEN -> JBColor.YELLOW.darker()
            CommentStatus.PENDING_USER -> JBColor.BLUE
            CommentStatus.PENDING_AGENT -> JBColor.ORANGE
            CommentStatus.FIXED -> JBColor.GREEN
            CommentStatus.RESOLVED -> JBColor.GRAY
            CommentStatus.WONTFIX -> JBColor.GRAY
        }
        return JLabel(comment.status.jsonValue).apply {
            foreground = color
        }
    }

    private fun createThreadEntryPanel(entry: ThreadEntry): JPanel {
        return JPanel(BorderLayout()).apply {
            val authorLabel = JBLabel(if (entry.isUserComment) "You" else "Claude").apply {
                foreground = if (entry.isUserComment) JBColor.BLUE else JBColor.GREEN.darker()
            }

            val timeLabel = JBLabel(formatTimestamp(entry.at)).apply {
                foreground = JBColor.GRAY
            }

            val headerRow = JPanel(BorderLayout()).apply {
                add(authorLabel, BorderLayout.WEST)
                add(timeLabel, BorderLayout.EAST)
            }

            val textArea = JBTextArea(entry.text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                background = null
            }

            add(headerRow, BorderLayout.NORTH)
            add(textArea, BorderLayout.CENTER)
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
        return arrayOf(
            object : DialogWrapperAction("Reply") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    val text = replyArea.text.trim()
                    if (text.isNotEmpty()) {
                        onReply(text)
                    }
                    close(OK_EXIT_CODE)
                }
            },
            cancelAction
        )
    }
}
