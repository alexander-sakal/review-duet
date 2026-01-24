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
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
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
                showInlineCommentEditor()
            }
        }

        private fun showInlineCommentEditor() {
            val reviewService = ReviewService(Path.of(basePath))

            if (!reviewService.hasActiveReview()) {
                JBPopupFactory.getInstance()
                    .createMessage("No active review. Start feature development first.")
                    .showInBestPositionFor(editor)
                return
            }

            // Get theme colors
            val colorsScheme = EditorColorsManager.getInstance().globalScheme
            val bgColor = colorsScheme.defaultBackground
            val fgColor = colorsScheme.defaultForeground
            val selectionColor = colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
                ?: colorsScheme.defaultBackground

            // Add line highlight using theme selection color
            val lineStartOffset = editor.document.getLineStartOffset(line - 1)
            val lineEndOffset = editor.document.getLineEndOffset(line - 1)

            val textAttributes = TextAttributes().apply {
                backgroundColor = selectionColor
            }

            val lineHighlighter = editor.markupModel.addRangeHighlighter(
                lineStartOffset,
                lineEndOffset,
                HighlighterLayer.SELECTION,
                textAttributes,
                HighlighterTargetArea.LINES_IN_RANGE
            )

            // Create inline comment panel using theme colors
            val panelBgColor = colorsScheme.getColor(EditorColors.GUTTER_BACKGROUND)
                ?: bgColor

            val panel = JPanel(BorderLayout()).apply {
                background = panelBgColor
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(colorsScheme.getColor(EditorColors.TEARLINE_COLOR) ?: fgColor, 1, 0, 1, 0),
                    JBUI.Borders.empty(8, 12)
                )
            }

            val textArea = JBTextArea().apply {
                rows = 2
                lineWrap = true
                wrapStyleWord = true
                background = bgColor
                foreground = fgColor
                caretColor = fgColor
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(colorsScheme.getColor(EditorColors.TEARLINE_COLOR) ?: fgColor, 1),
                    JBUI.Borders.empty(6)
                )
                putClientProperty("JTextField.placeholderText", "Add a comment...")
            }

            val scrollPane = JBScrollPane(textArea).apply {
                border = null
                preferredSize = Dimension(500, 60)
            }

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                background = panelBgColor
                border = JBUI.Borders.emptyTop(8)
            }

            var popup: JBPopup? = null
            var scrollListener: VisibleAreaListener? = null

            val cleanup = {
                editor.markupModel.removeHighlighter(lineHighlighter)
                scrollListener?.let { editor.scrollingModel.removeVisibleAreaListener(it) }
            }

            val cancelButton = JButton("Cancel").apply {
                addActionListener {
                    cleanup()
                    popup?.cancel()
                }
            }

            val submitButton = JButton("Add Comment").apply {
                addActionListener {
                    val text = textArea.text.trim()
                    if (text.isNotBlank()) {
                        reviewService.addComment(filePath, line, text)
                    }
                    cleanup()
                    popup?.cancel()
                }
            }

            // Make submit button primary
            submitButton.putClientProperty("JButton.buttonType", "default")

            buttonPanel.add(cancelButton)
            buttonPanel.add(submitButton)

            panel.add(scrollPane, BorderLayout.CENTER)
            panel.add(buttonPanel, BorderLayout.SOUTH)

            // Create popup without decorations
            popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, textArea)
                .setRequestFocus(true)
                .setFocusable(true)
                .setMovable(false)
                .setResizable(false)
                .setCancelOnClickOutside(false)
                .setCancelOnOtherWindowOpen(false)
                .setCancelKeyEnabled(true)
                .setCancelCallback {
                    cleanup()
                    true
                }
                .createPopup()

            // Function to calculate popup position
            fun calculatePopupPosition(): Point {
                val visualPosition = editor.offsetToVisualPosition(lineEndOffset)
                val point = editor.visualPositionToXY(visualPosition)
                val visibleArea = editor.scrollingModel.visibleArea

                // Align to left edge of visible editor area
                val xPos = visibleArea.x + 20
                val yPos = point.y + editor.lineHeight

                return Point(xPos, yPos)
            }

            // Add scroll listener to reposition popup
            scrollListener = VisibleAreaListener { _: VisibleAreaEvent ->
                popup?.let { p ->
                    if (p.isVisible) {
                        val newPos = calculatePopupPosition()
                        val editorComponent = editor.contentComponent
                        val screenPoint = RelativePoint(editorComponent, newPos).screenPoint
                        p.setLocation(screenPoint)
                    }
                }
            }
            editor.scrollingModel.addVisibleAreaListener(scrollListener!!)

            // Initial position
            val initialPos = calculatePopupPosition()
            val editorComponent = editor.contentComponent
            popup.show(RelativePoint(editorComponent, initialPos))

            // Focus the text area
            SwingUtilities.invokeLater {
                textArea.requestFocusInWindow()
            }
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
