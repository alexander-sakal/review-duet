package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import javax.swing.*

class CommentListPanel(
    private val comments: List<Comment>,
    private val onCommentSelected: (Comment) -> Unit
) : JBScrollPane() {

    private val listModel = DefaultListModel<Comment>()
    private val commentList = JBList(listModel)

    init {
        comments.forEach { listModel.addElement(it) }

        commentList.cellRenderer = CommentCellRenderer()
        commentList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        commentList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = commentList.selectedValue
                if (selected != null) {
                    // Clear selection immediately to prevent re-triggering
                    commentList.clearSelection()
                    onCommentSelected(selected)
                }
            }
        }

        setViewportView(commentList)
        border = JBUI.Borders.empty()
    }

    private class CommentCellRenderer : ColoredListCellRenderer<Comment>() {
        override fun customizeCellRenderer(
            list: JList<out Comment>,
            value: Comment,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val statusColor = when (value.status) {
                CommentStatus.OPEN -> JBColor.YELLOW.darker()
                CommentStatus.PENDING_USER -> JBColor.BLUE
                CommentStatus.PENDING_AGENT -> JBColor.ORANGE
                CommentStatus.FIXED -> JBColor.GREEN
                CommentStatus.RESOLVED -> JBColor.GRAY
                CommentStatus.WONTFIX -> JBColor.GRAY
            }

            append("#${value.id} ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append("[${value.status.jsonValue}] ", SimpleTextAttributes(
                SimpleTextAttributes.STYLE_BOLD, statusColor
            ))
            append("${value.file}:${value.line}")

            value.firstUserMessage?.let { msg ->
                append(" - ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(msg.take(40) + if (msg.length > 40) "..." else "")
            }
        }
    }

    fun refresh(newComments: List<Comment>) {
        listModel.clear()
        newComments.forEach { listModel.addElement(it) }
    }
}
