package com.codereview.local.diff

import com.codereview.local.services.ReviewService
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.nio.file.Path
import javax.swing.*

class DiffCommentExtension : DiffExtension() {

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        if (viewer !is TwosideTextDiffViewer) return

        val project = context.project ?: return
        val basePath = project.basePath ?: return

        // Get the "new" side editor (right side - the changes)
        val editor = viewer.editor2
        val title = request.title ?: return

        // Extract file path from title (format: "path/to/file.kt (review-r0 → review-r1)")
        val filePath = title.substringBefore(" (")

        setupGutterComments(editor, filePath, basePath, project)
    }

    private fun setupGutterComments(editor: Editor, filePath: String, basePath: String, project: Project) {
        var currentHighlighter: RangeHighlighter? = null
        var currentLine: Int = -1

        // Show "+" icon on hover
        editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                val line = e.logicalPosition.line

                // Remove old highlighter if line changed
                if (line != currentLine) {
                    currentHighlighter?.let {
                        editor.markupModel.removeHighlighter(it)
                    }
                    currentHighlighter = null
                    currentLine = -1
                }

                // Show icon when hovering on any line
                if (line != currentLine && line >= 0 && line < editor.document.lineCount) {
                    currentLine = line
                    val startOffset = editor.document.getLineStartOffset(line)
                    val endOffset = editor.document.getLineEndOffset(line)

                    currentHighlighter = editor.markupModel.addRangeHighlighter(
                        startOffset,
                        endOffset,
                        HighlighterLayer.LAST,
                        null,
                        HighlighterTargetArea.LINES_IN_RANGE
                    ).apply {
                        gutterIconRenderer = AddCommentGutterIcon(editor, project, filePath, line + 1, basePath)
                    }
                }
            }
        })

        // Remove highlighter when mouse leaves
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseExited(e: EditorMouseEvent) {
                currentHighlighter?.let {
                    editor.markupModel.removeHighlighter(it)
                }
                currentHighlighter = null
                currentLine = -1
            }
        })
    }

    private class AddCommentGutterIcon(
        private val editor: Editor,
        private val project: Project,
        private val filePath: String,
        private val line: Int,
        private val basePath: String
    ) : GutterIconRenderer() {

        override fun getIcon(): Icon = AllIcons.General.Add

        override fun getTooltipText(): String = "Add review comment"

        override fun getClickAction(): AnAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                showInlineCommentPopup()
            }
        }

        private fun showInlineCommentPopup() {
            val reviewService = ReviewService(Path.of(basePath))

            if (!reviewService.hasActiveReview()) {
                JBPopupFactory.getInstance()
                    .createMessage("No active review. Start feature development first.")
                    .showInBestPositionFor(editor)
                return
            }

            // Create the comment input panel
            val panel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8)
                preferredSize = Dimension(400, 120)
            }

            val headerLabel = JLabel("$filePath:$line").apply {
                border = JBUI.Borders.emptyBottom(5)
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            }

            val textArea = JBTextArea().apply {
                rows = 3
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(5)
            }

            val scrollPane = JBScrollPane(textArea).apply {
                preferredSize = Dimension(380, 60)
            }

            val buttonPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                border = JBUI.Borders.emptyTop(8)
            }

            var popup: JBPopup? = null

            val cancelButton = JButton("Cancel").apply {
                addActionListener { popup?.cancel() }
            }

            val submitButton = JButton("Add Comment").apply {
                addActionListener {
                    val text = textArea.text.trim()
                    if (text.isNotBlank()) {
                        reviewService.addComment(filePath, line, text)
                        popup?.cancel()
                    }
                }
            }

            buttonPanel.add(Box.createHorizontalGlue())
            buttonPanel.add(cancelButton)
            buttonPanel.add(Box.createHorizontalStrut(8))
            buttonPanel.add(submitButton)

            panel.add(headerLabel, BorderLayout.NORTH)
            panel.add(scrollPane, BorderLayout.CENTER)
            panel.add(buttonPanel, BorderLayout.SOUTH)

            // Create and show popup below the line
            popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, textArea)
                .setRequestFocus(true)
                .setFocusable(true)
                .setMovable(true)
                .setResizable(true)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .createPopup()

            // Position below the current line
            val point = editor.visualPositionToXY(editor.offsetToVisualPosition(
                editor.document.getLineEndOffset(line - 1)
            ))
            val editorComponent = editor.contentComponent
            popup.show(RelativePoint(editorComponent, Point(point.x, point.y + editor.lineHeight)))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AddCommentGutterIcon) return false
            return filePath == other.filePath && line == other.line
        }

        override fun hashCode(): Int {
            return 31 * filePath.hashCode() + line
        }
    }
}
