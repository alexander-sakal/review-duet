package com.codereview.local.diff

import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus
import com.codereview.local.model.FileReviewData
import com.codereview.local.services.FileReviewService
import com.codereview.local.ui.FileReviewPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
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
import com.intellij.ui.hover.HoverStateListener
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Installs a gutter "+" icon and inline comment form on regular (non-diff) editors when the open
 * file has an active file review. Only fires when the review storage file exists; all other editors
 * are untouched.
 */
class FileReviewEditorCommentExtension : EditorFactoryListener {

    companion object {
        internal val STATE_KEY = Key.create<EditorState>("FileReviewEditorCommentExtension.state")

        /**
         * Installs the gutter "+" affordance on [editor] for the given [project].
         *
         * Idempotent: if the affordance is already installed (STATE_KEY is set), returns immediately.
         * Safe to call from outside [editorCreated] — e.g. retroactively when a new review is created
         * while the editor is already open.
         */
        internal fun install(editor: Editor, project: Project) {
            if (editor.getUserData(STATE_KEY) != null) return
            val vfile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
            val targetFile = Path.of(vfile.path)
            installGutterAffordance(editor, project, targetFile)

            // Render existing comments as inlays now that the state is set.
            val state = editor.getUserData(STATE_KEY) ?: return
            val service = FileReviewService.getInstance(project)
            val review = service.openOrCreate(targetFile)
            val data = try { review.loadOrInit() } catch (_: Exception) { return }
            ApplicationManager.getApplication().invokeLater {
                renderCommentInlays(editor, project, data, state)
            }
        }

        /**
         * Renders [FileComment] inlays below each anchored line in [editor].
         * Disposes any previously rendered inlays first, making this idempotent.
         * Must be called on the EDT.
         */
        internal fun renderCommentInlays(
            editor: Editor,
            project: Project,
            data: FileReviewData,
            state: EditorState
        ) {
            state.commentInlays.forEach { it.dispose() }
            state.commentInlays.clear()

            val editorImpl = editor as? EditorImpl ?: return
            val vfile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
            val targetFile = Path.of(vfile.path)

            for (c in data.comments) {
                val lineIdx = (c.line - 1).coerceIn(0, maxOf(0, editor.document.lineCount - 1))
                val offset = editor.document.getLineEndOffset(lineIdx)

                val commentPanel = createFileCommentPanel(c, editor, project, targetFile, state)
                val wrappedPanel = EditorWidthPanel(editorImpl, commentPanel)

                val properties = EditorEmbeddedComponentManager.Properties(
                    EditorEmbeddedComponentManager.ResizePolicy.none(),
                    null,
                    false,
                    false,
                    0,
                    offset
                )

                val inlay = EditorEmbeddedComponentManager.getInstance()
                    .addComponent(editorImpl, wrappedPanel, properties)
                inlay?.let { state.commentInlays.add(it) }
            }
        }

        /**
         * Re-renders inlays for [targetFile] in every open main editor that shows that file.
         * Safe to call from any thread — dispatches to the EDT internally.
         */
        internal fun refreshInlaysForFile(project: Project, targetFile: Path) {
            ApplicationManager.getApplication().invokeLater {
                val targetPath = targetFile.toString()
                EditorFactory.getInstance().allEditors.forEach { editor ->
                    val vfile = FileDocumentManager.getInstance().getFile(editor.document) ?: return@forEach
                    if (vfile.path != targetPath || editor.editorKind != EditorKind.MAIN_EDITOR) return@forEach
                    val state = editor.getUserData(STATE_KEY) ?: return@forEach
                    val service = FileReviewService.getInstance(project)
                    val review = service.openOrCreate(targetFile)
                    val data = try { review.loadOrInit() } catch (_: Exception) { return@forEach }
                    renderCommentInlays(editor, project, data, state)
                }
            }
        }

        /**
         * Builds a panel displaying a single [FileComment] as an inlay with Edit and Delete controls.
         * Edit flips to an inline form pre-filled with the current text; Delete shows a confirmation
         * dialog before removing the comment via [FileReviewPanel].
         *
         * Visual treatment:
         * - OPEN — normal foreground text, amber status badge.
         * - FIXED — dimmed text (gray), green status badge.
         * - OUTDATED — dimmed text (gray) with HTML strikethrough, gray status badge.
         */
        private fun createFileCommentPanel(
            comment: FileComment,
            editor: Editor,
            project: Project,
            targetFile: Path,
            state: EditorState
        ): JComponent {
            val colorsScheme = EditorColorsManager.getInstance().globalScheme
            val bgColor = colorsScheme.defaultBackground
            val fgColor = colorsScheme.defaultForeground
            val borderColor = colorsScheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()
            val inlayPadding = 6

            val cardLayout = CardLayout()
            var currentCard: JComponent? = null
            val cardPanel = object : JPanel(cardLayout) {
                override fun getPreferredSize(): Dimension =
                    currentCard?.preferredSize ?: super.getPreferredSize()
            }.apply { isOpaque = false }

            var displayPanel: JComponent? = null
            var editPanel: JComponent? = null

            displayPanel = createFileCommentDisplayPanel(
                comment, bgColor, fgColor, borderColor, inlayPadding,
                onEdit = {
                    currentCard = editPanel
                    cardLayout.show(cardPanel, "edit")
                    cardPanel.revalidate()
                    cardPanel.repaint()
                },
                onDelete = {
                    val result = Messages.showYesNoDialog(
                        project,
                        "Delete this comment?",
                        "Delete Comment",
                        Messages.getQuestionIcon()
                    )
                    if (result == Messages.YES) {
                        val panel = ToolWindowManager.getInstance(project)
                            .getToolWindow("Review Duet Files")
                            ?.contentManager?.contents?.firstOrNull()
                            ?.component as? FileReviewPanel
                        if (panel != null) {
                            panel.deleteComment(targetFile, comment.id)
                        } else {
                            Messages.showInfoMessage(
                                project,
                                "Open the file via \"Review File with Review Duet\" first to manage comments.",
                                "Review Duet Files panel not open"
                            )
                        }
                    }
                }
            )

            editPanel = createFileCommentEditPanel(
                comment, editor, project, targetFile, state, bgColor, fgColor, borderColor, inlayPadding,
                onCancel = {
                    currentCard = displayPanel
                    cardLayout.show(cardPanel, "display")
                    cardPanel.revalidate()
                    cardPanel.repaint()
                }
            )

            cardPanel.add(displayPanel, "display")
            cardPanel.add(editPanel, "edit")
            currentCard = displayPanel
            cardLayout.show(cardPanel, "display")

            return JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(4, 8, 4, 8)
                add(cardPanel, BorderLayout.NORTH)
            }
        }

        private fun createFileCommentDisplayPanel(
            comment: FileComment,
            bgColor: Color,
            fgColor: Color,
            borderColor: Color,
            inlayPadding: Int,
            onEdit: () -> Unit,
            onDelete: () -> Unit
        ): JComponent {
            val dimColor = UIUtil.getContextHelpForeground()
            val statusColor = when (comment.status) {
                FileCommentStatus.OPEN -> Color(255, 193, 7)
                FileCommentStatus.FIXED -> Color(76, 175, 80)
                FileCommentStatus.OUTDATED -> Color(158, 158, 158)
            }

            val hoverPanel = object : JPanel(BorderLayout()) {
                override fun getBackground(): Color = super.getBackground() ?: bgColor
            }.apply {
                isOpaque = true
                background = bgColor
                cursor = Cursor.getDefaultCursor()
                border = BorderFactory.createCompoundBorder(
                    RoundedLineBorder(borderColor, 8, 1),
                    JBUI.Borders.empty(inlayPadding)
                )
            }

            val contentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
            }

            val headerPanel = object : JPanel(BorderLayout()) {
                override fun getMaximumSize(): Dimension {
                    val pref = preferredSize
                    return Dimension(Int.MAX_VALUE, pref.height)
                }
            }.apply { isOpaque = false }

            val tagWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.empty()
                add(JBLabel("#${comment.id}").apply {
                    font = JBFont.small()
                    foreground = dimColor
                })
                add(JBLabel(" ${comment.status.jsonValue} ").apply {
                    font = JBFont.small()
                    foreground = statusColor
                    border = BorderFactory.createLineBorder(statusColor, 1)
                })
            }

            val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                isVisible = false
            }

            val editButton = InlineIconButton(
                AllIcons.General.Inline_edit,
                AllIcons.General.Inline_edit_hovered
            ).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                actionListener = java.awt.event.ActionListener { onEdit() }
            }
            actionsPanel.add(editButton)

            val deleteButton = InlineIconButton(
                AllIcons.Actions.GC,
                AllIcons.Actions.GC
            ).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                actionListener = java.awt.event.ActionListener { onDelete() }
            }
            actionsPanel.add(deleteButton)

            headerPanel.add(tagWrapper, BorderLayout.WEST)
            headerPanel.add(actionsPanel, BorderLayout.EAST)
            contentPanel.add(headerPanel)
            contentPanel.add(javax.swing.Box.createVerticalStrut(4))

            // Comment text — dimmed for FIXED; strikethrough + dimmed for OUTDATED
            if (comment.status == FileCommentStatus.OUTDATED) {
                val strikeLabel = JBLabel("<html><strike>${escapeHtml(comment.text)}</strike></html>").apply {
                    foreground = dimColor
                    font = UIUtil.getLabelFont()
                    border = JBUI.Borders.emptyTop(4)
                }
                contentPanel.add(strikeLabel)
            } else {
                val textFg = if (comment.status == FileCommentStatus.OPEN) fgColor else dimColor
                val textArea = object : JTextArea(comment.text) {
                    override fun getMaximumSize(): Dimension {
                        val pref = preferredSize
                        return Dimension(Int.MAX_VALUE, pref.height)
                    }
                }.apply {
                    isEditable = false
                    isOpaque = false
                    lineWrap = true
                    wrapStyleWord = true
                    foreground = textFg
                    font = UIUtil.getLabelFont()
                    border = JBUI.Borders.empty()
                    cursor = Cursor.getDefaultCursor()
                }
                contentPanel.add(textArea)
            }

            contentPanel.add(javax.swing.Box.createVerticalGlue())
            hoverPanel.add(contentPanel, BorderLayout.NORTH)

            object : HoverStateListener() {
                override fun hoverChanged(component: java.awt.Component, hovered: Boolean) {
                    actionsPanel.isVisible = hovered
                    hoverPanel.background = if (hovered) {
                        JBColor(Color(0, 0, 0, 20), Color(255, 255, 255, 20))
                    } else {
                        bgColor
                    }
                }
            }.addTo(hoverPanel)

            return hoverPanel
        }

        private fun createFileCommentEditPanel(
            comment: FileComment,
            editor: Editor,
            project: Project,
            targetFile: Path,
            state: EditorState,
            bgColor: Color,
            fgColor: Color,
            borderColor: Color,
            inlayPadding: Int,
            onCancel: () -> Unit
        ): JComponent {
            val originalText = comment.text

            val panel = JPanel(BorderLayout()).apply {
                isOpaque = true
                background = bgColor
                cursor = Cursor.getDefaultCursor()
                border = BorderFactory.createCompoundBorder(
                    RoundedLineBorder(borderColor, 8, 1),
                    JBUI.Borders.empty(inlayPadding)
                )
            }

            val textArea = JBTextArea(originalText).apply {
                rows = 3
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

            val hintLabel = JBLabel("Ctrl+Enter to save").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBFont.small()
            }

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
                cursor = Cursor.getDefaultCursor()
            }

            val saveAndRefresh = {
                val newText = textArea.text.trim()
                if (newText.isNotBlank() && newText != originalText) {
                    val filePanel = ToolWindowManager.getInstance(project)
                        .getToolWindow("Review Duet Files")
                        ?.contentManager?.contents?.firstOrNull()
                        ?.component as? FileReviewPanel
                    if (filePanel != null) {
                        filePanel.editComment(targetFile, comment.id, newText)
                    } else {
                        Messages.showInfoMessage(
                            project,
                            "Open the file via \"Review File with Review Duet\" first to manage comments.",
                            "Review Duet Files panel not open"
                        )
                    }
                } else {
                    // Re-render inlays to flip back to display mode even if text unchanged
                    refreshInlaysForFile(project, targetFile)
                }
            }

            buttonPanel.add(JButton("Cancel").apply {
                isContentAreaFilled = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener { onCancel() }
            })

            buttonPanel.add(JButton("Save").apply {
                isContentAreaFilled = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener { saveAndRefresh() }
            })

            bottomPanel.add(hintLabel, BorderLayout.WEST)
            bottomPanel.add(buttonPanel, BorderLayout.EAST)

            panel.add(scrollPane, BorderLayout.CENTER)
            panel.add(bottomPanel, BorderLayout.SOUTH)

            textArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control ENTER"), "save")
            textArea.actionMap.put("save", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) { saveAndRefresh() }
            })

            textArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel")
            textArea.actionMap.put("cancel", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) { onCancel() }
            })

            panel.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentShown(e: java.awt.event.ComponentEvent?) {
                    SwingUtilities.invokeLater {
                        textArea.requestFocusInWindow()
                        textArea.selectAll()
                    }
                }
            })

            return DiffCommentExtension.wrapForFocusIsolation(panel)
        }

        private fun escapeHtml(text: String): String =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

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
    }

    internal class EditorState(
        val motionListener: EditorMouseMotionListener,
        val mouseListener: EditorMouseListener,
        var currentHighlighter: RangeHighlighter? = null,
        var currentLine: Int = -1,
        val commentInlays: MutableList<Inlay<*>> = mutableListOf()
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

        install(editor, project)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val state = editor.getUserData(STATE_KEY) ?: return
        editor.removeEditorMouseMotionListener(state.motionListener)
        editor.removeEditorMouseListener(state.mouseListener)
        state.currentHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        state.currentHighlighter = null
        state.commentInlays.forEach { it.dispose() }
        state.commentInlays.clear()
        editor.putUserData(STATE_KEY, null)
    }

    private class AddCommentGutterIcon(
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
