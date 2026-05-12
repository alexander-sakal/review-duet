package com.codereview.local.diff

import com.codereview.local.services.FileReviewService
import com.codereview.local.ui.FileReviewPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Installs a gutter "+" icon and inline comment form on regular (non-diff) editors when the open
 * file has an active file review. Only fires when the review storage file exists; all other editors
 * are untouched.
 */
class FileReviewEditorCommentExtension : EditorFactoryListener {

    companion object {
        private val STATE_KEY = Key.create<EditorState>("FileReviewEditorCommentExtension.state")
    }

    private class EditorState(
        val motionListener: EditorMouseMotionListener,
        val mouseListener: EditorMouseListener,
        var currentHighlighter: RangeHighlighter? = null,
        var currentLine: Int = -1
    )

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        // Only handle real editors (not diff viewers, console, etc.)
        if (editor.editorKind != com.intellij.openapi.editor.EditorKind.MAIN_EDITOR) return
        val project = editor.project ?: return

        // Resolve the virtual file for this editor
        val vfile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val targetFile = Path.of(vfile.path)

        // Only install the affordance when a file review storage file exists
        val storagePath = FileReviewService.getInstance(project).resolveStoragePath(targetFile) ?: return
        if (!storagePath.toFile().exists()) return

        installGutterAffordance(editor, project, targetFile)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val state = editor.getUserData(STATE_KEY) ?: return
        editor.removeEditorMouseMotionListener(state.motionListener)
        editor.removeEditorMouseListener(state.mouseListener)
        state.currentHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        state.currentHighlighter = null
        editor.putUserData(STATE_KEY, null)
    }

    private fun installGutterAffordance(editor: Editor, project: Project, targetFile: Path) {
        val state = EditorState(
            motionListener = object : EditorMouseMotionListener {
                override fun mouseMoved(e: EditorMouseEvent) {
                    val editorState = editor.getUserData(STATE_KEY) ?: return
                    val line = e.logicalPosition.line

                    if (line != editorState.currentLine) {
                        editorState.currentHighlighter?.let { editor.markupModel.removeHighlighter(it) }
                        editorState.currentHighlighter = null
                        editorState.currentLine = -1
                    }

                    if (line != editorState.currentLine && line >= 0 && line < editor.document.lineCount) {
                        editorState.currentLine = line
                        val startOffset = editor.document.getLineStartOffset(line)
                        val endOffset = editor.document.getLineEndOffset(line)

                        editorState.currentHighlighter = editor.markupModel.addRangeHighlighter(
                            startOffset,
                            endOffset,
                            HighlighterLayer.LAST,
                            null,
                            HighlighterTargetArea.LINES_IN_RANGE
                        ).apply {
                            gutterIconRenderer = AddCommentGutterIcon(editor, project, targetFile, line + 1)
                        }
                    }
                }
            },
            mouseListener = object : EditorMouseListener {
                override fun mouseExited(e: EditorMouseEvent) {
                    val editorState = editor.getUserData(STATE_KEY) ?: return
                    editorState.currentHighlighter?.let { editor.markupModel.removeHighlighter(it) }
                    editorState.currentHighlighter = null
                    editorState.currentLine = -1
                }
            }
        )

        editor.putUserData(STATE_KEY, state)
        editor.addEditorMouseMotionListener(state.motionListener)
        editor.addEditorMouseListener(state.mouseListener)
    }

    private fun showInlineCommentForm(editor: Editor, project: Project, targetFile: Path, line: Int) {
        val editorImpl = editor as? EditorImpl ?: return
        val offset = editor.document.getLineEndOffset(line - 1)

        var formInlay: Inlay<*>? = null
        val onDismiss = {
            formInlay?.dispose()
            Unit
        }

        val formPanel = createNewCommentPanel(editor, project, targetFile, line, onDismiss)
        val wrappedPanel = EditorWidthPanel(editorImpl, formPanel)

        val properties = EditorEmbeddedComponentManager.Properties(
            EditorEmbeddedComponentManager.ResizePolicy.none(),
            null,
            false,
            false,
            100,
            offset
        )

        formInlay = EditorEmbeddedComponentManager.getInstance()
            .addComponent(editorImpl, wrappedPanel, properties)
    }

    private fun createNewCommentPanel(
        editor: Editor,
        project: Project,
        targetFile: Path,
        line: Int,
        onDismiss: () -> Unit
    ): JComponent {
        val colorsScheme = EditorColorsManager.getInstance().globalScheme
        val bgColor = colorsScheme.defaultBackground
        val fgColor = colorsScheme.defaultForeground
        val borderColor = colorsScheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()

        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = bgColor
            cursor = Cursor.getDefaultCursor()
            border = BorderFactory.createCompoundBorder(
                RoundedLineBorder(borderColor, 8, 1),
                JBUI.Borders.empty(8)
            )
        }

        val textArea = JBTextArea().apply {
            rows = 2
            lineWrap = true
            wrapStyleWord = true
            isEditable = true
            background = UIUtil.getTextFieldBackground()
            foreground = fgColor
            caretColor = fgColor
            border = JBUI.Borders.empty(6)
        }

        val scrollPane = JBScrollPane(textArea).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
        }

        val bottomPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            cursor = Cursor.getDefaultCursor()
            border = JBUI.Borders.emptyTop(8)
        }

        val hintLabel = JBLabel("Ctrl+Enter to submit").apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            cursor = Cursor.getDefaultCursor()
        }

        val submitAction = {
            val text = textArea.text.trim()
            if (text.isNotBlank()) {
                val panel = ToolWindowManager.getInstance(project)
                    .getToolWindow("Review Duet Files")
                    ?.contentManager?.contents?.firstOrNull()
                    ?.component as? FileReviewPanel
                if (panel != null) {
                    panel.addComment(targetFile, line, text)
                } else {
                    Messages.showInfoMessage(
                        project,
                        "Open the file via \"Review File with Review Duet\" first to add comments.",
                        "Review Duet Files panel not open"
                    )
                }
            }
            onDismiss()
        }

        buttonPanel.add(JButton("Cancel").apply {
            isContentAreaFilled = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { onDismiss() }
        })

        buttonPanel.add(JButton("Add Comment").apply {
            isContentAreaFilled = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { submitAction() }
        })

        bottomPanel.add(hintLabel, BorderLayout.WEST)
        bottomPanel.add(buttonPanel, BorderLayout.EAST)

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        textArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control ENTER"), "submit")
        textArea.actionMap.put("submit", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) { submitAction() }
        })

        textArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel")
        textArea.actionMap.put("cancel", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) { onDismiss() }
        })

        SwingUtilities.invokeLater { textArea.requestFocusInWindow() }

        val outerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            cursor = Cursor.getDefaultCursor()
            border = JBUI.Borders.empty(4, 8, 4, 8)
            add(panel, BorderLayout.CENTER)
        }
        return DiffCommentExtension.wrapForFocusIsolation(outerPanel)
    }

    private inner class AddCommentGutterIcon(
        private val editor: Editor,
        private val project: Project,
        private val targetFile: Path,
        private val line: Int,
    ) : GutterIconRenderer() {

        override fun getIcon() = AllIcons.General.Add

        override fun getTooltipText(): String = "Add file review comment"

        override fun getClickAction(): AnAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                showInlineCommentForm(editor, project, targetFile, line)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AddCommentGutterIcon) return false
            return targetFile == other.targetFile && line == other.line
        }

        override fun hashCode(): Int = 31 * targetFile.hashCode() + line
    }

    /** Matches the width of the editor viewport for embedded inlay components. */
    private class EditorWidthPanel(
        private val editor: EditorImpl,
        private val content: JComponent
    ) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = JBUI.Borders.empty()
            add(content, BorderLayout.CENTER)

            editor.scrollPane.viewport.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                    revalidate()
                    repaint()
                }
            })
        }

        override fun getPreferredSize(): Dimension {
            val viewport = editor.scrollPane.viewport
            val scrollbarWidth = editor.scrollPane.verticalScrollBar.width
            val editorWidth = maxOf(viewport.width - scrollbarWidth, 200)
            return Dimension(editorWidth, content.preferredSize.height)
        }

        override fun getMaximumSize(): Dimension = preferredSize
    }
}
