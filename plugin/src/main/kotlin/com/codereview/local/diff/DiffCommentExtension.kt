package com.codereview.local.diff

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.services.ReviewService
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.hover.HoverStateListener
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.*

class DiffCommentExtension : DiffExtension() {

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        if (viewer !is TwosideTextDiffViewer) return

        val project = context.project ?: return
        val basePath = project.basePath ?: return

        val editor = viewer.editor2
        val title = request.title ?: return

        // Extract file path from title
        val filePath = extractFilePath(title)

        val commentInlays = mutableListOf<Inlay<*>>()

        setupGutterComments(editor, filePath, basePath, project, commentInlays)

        // Add reviewed action to context
        setupReviewedAction(viewer, context, filePath, basePath)

        // Display existing comments
        SwingUtilities.invokeLater {
            displayComments(editor, filePath, basePath, commentInlays)
        }
    }

    private fun setupReviewedAction(viewer: TwosideTextDiffViewer, context: DiffContext, filePath: String, basePath: String) {
        val reviewService = ReviewService(Path.of(basePath))

        // Store the file path in the context for the action to access
        context.putUserData(FILE_PATH_KEY, filePath)
        context.putUserData(BASE_PATH_KEY, basePath)

        // Add a review toolbar panel to the viewer
        SwingUtilities.invokeLater {
            addReviewToolbar(viewer, reviewService, filePath)
        }
    }

    private fun addReviewToolbar(viewer: TwosideTextDiffViewer, reviewService: ReviewService, filePath: String) {
        val viewerComponent = viewer.component

        // Find the top-level component that contains the toolbar
        val parent = viewerComponent.parent
        if (parent is JComponent) {
            val reviewPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                isOpaque = true
                background = UIUtil.getPanelBackground()
                border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)

                add(JBLabel("Review:").apply {
                    foreground = UIUtil.getLabelDisabledForeground()
                })

                val button = JButton().apply {
                    fun updateState() {
                        val isReviewed = reviewService.isFileReviewed(filePath)
                        icon = if (isReviewed) AllIcons.Actions.Checked else AllIcons.Actions.CheckOut
                        text = if (isReviewed) "Reviewed" else "Mark Reviewed"
                    }
                    updateState()
                    addActionListener {
                        reviewService.toggleFileReviewed(filePath)
                        updateState()
                    }
                    isFocusPainted = false
                    isContentAreaFilled = false
                    border = JBUI.Borders.empty(2, 8)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }
                add(button)
            }

            // Try to add at the top
            if (parent.layout is BorderLayout) {
                // Check if NORTH is already occupied
                val existingNorth = (parent.layout as BorderLayout).getLayoutComponent(BorderLayout.NORTH)
                if (existingNorth != null) {
                    // Wrap existing north component with our panel
                    val wrapper = JPanel(BorderLayout()).apply {
                        add(reviewPanel, BorderLayout.NORTH)
                        add(existingNorth, BorderLayout.CENTER)
                    }
                    parent.remove(existingNorth)
                    parent.add(wrapper, BorderLayout.NORTH)
                } else {
                    parent.add(reviewPanel, BorderLayout.NORTH)
                }
                parent.revalidate()
                parent.repaint()
            }
        }
    }

    companion object {
        val FILE_PATH_KEY = com.intellij.openapi.util.Key.create<String>("CodeReview.FilePath")
        val BASE_PATH_KEY = com.intellij.openapi.util.Key.create<String>("CodeReview.BasePath")

        /**
         * Suppresses the outer editor context so that editor actions don't steal key events
         * from embedded input components. This prevents "Read-only view" messages when
         * typing in comment forms embedded in the diff viewer.
         */
        private fun suppressOuterEditorData(sink: DataSink) {
            arrayOf(
                CommonDataKeys.EDITOR,
                CommonDataKeys.HOST_EDITOR,
                CommonDataKeys.CARET,
                CommonDataKeys.VIRTUAL_FILE, CommonDataKeys.VIRTUAL_FILE_ARRAY,
                CommonDataKeys.LANGUAGE,
                CommonDataKeys.PSI_FILE, CommonDataKeys.PSI_ELEMENT,
                PlatformCoreDataKeys.FILE_EDITOR,
                PlatformCoreDataKeys.PSI_ELEMENT_ARRAY
            ).forEach {
                sink.setNull(it)
            }
        }

        /**
         * Wraps a component to create a proper focus isolation boundary that prevents
         * the diff editor from intercepting key events.
         */
        fun wrapForFocusIsolation(component: JComponent): JComponent {
            return JPanel(BorderLayout()).apply {
                isOpaque = false
                isFocusCycleRoot = true
                isFocusTraversalPolicyProvider = true
                focusTraversalPolicy = LayoutFocusTraversalPolicy()
                // Only Escape should exit to the editor
                setFocusTraversalKeys(
                    KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS,
                    setOf(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0))
                )
                add(UiDataProvider.wrapComponent(component) { sink ->
                    suppressOuterEditorData(sink)
                }, BorderLayout.CENTER)
            }
        }
    }

    private fun extractFilePath(title: String): String {
        // Handle formats like "Review: path/to/file" or "path/to/file (ref1 → ref2)"
        return when {
            title.startsWith("Review:") -> title.removePrefix("Review:").trim()
            title.contains(" (") -> title.substringBefore(" (")
            else -> title
        }
    }

    private fun displayComments(
        editor: Editor,
        filePath: String,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>
    ) {
        refreshCommentInlays(editor, filePath, basePath, commentInlays)
    }

    private fun refreshCommentInlays(
        editor: Editor,
        filePath: String,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>
    ) {
        val reviewService = ReviewService(Path.of(basePath))
        val reviewData = reviewService.loadReviewData() ?: return

        commentInlays.forEach { it.dispose() }
        commentInlays.clear()

        val editorImpl = editor as? EditorImpl ?: return
        val fileComments = reviewData.comments.filter { it.file == filePath }

        for (comment in fileComments) {
            val line = comment.line - 1
            if (line >= 0 && line < editor.document.lineCount) {
                val offset = editor.document.getLineEndOffset(line)

                val commentPanel = createCommentPanel(comment, editor, filePath, basePath, commentInlays)
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

                inlay?.let { commentInlays.add(it) }
            }
        }
    }

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
            val editorWidth = calculateEditorWidth()
            val contentHeight = content.preferredSize.height
            return Dimension(editorWidth, contentHeight)
        }

        override fun getMaximumSize(): Dimension = preferredSize

        private fun calculateEditorWidth(): Int {
            val viewport = editor.scrollPane.viewport
            val scrollbarWidth = editor.scrollPane.verticalScrollBar.width
            return maxOf(viewport.width - scrollbarWidth, 200)
        }
    }

    private fun createCommentPanel(
        comment: Comment,
        editor: Editor,
        filePath: String,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>
    ): JComponent {
        val colorsScheme = EditorColorsManager.getInstance().globalScheme
        val bgColor = colorsScheme.defaultBackground
        val fgColor = colorsScheme.defaultForeground
        val borderColor = colorsScheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()

        val inlayPadding = 8
        val verticalGap = 4

        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout).apply { isOpaque = false }

        val displayPanel = createDisplayPanel(
            comment, editor, filePath, basePath, commentInlays,
            bgColor, fgColor, borderColor, inlayPadding, verticalGap
        ) {
            cardLayout.show(cardPanel, "edit")
            cardPanel.revalidate()
            cardPanel.repaint()
        }

        val editPanel = createEditPanel(
            comment, editor, filePath, basePath, commentInlays,
            bgColor, fgColor, borderColor, inlayPadding
        ) {
            cardLayout.show(cardPanel, "display")
            cardPanel.revalidate()
            cardPanel.repaint()
        }

        cardPanel.add(displayPanel, "display")
        cardPanel.add(editPanel, "edit")
        cardLayout.show(cardPanel, "display")

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 8, 4, 8)
            add(cardPanel, BorderLayout.CENTER)
        }
    }

    private fun createDisplayPanel(
        comment: Comment,
        editor: Editor,
        filePath: String,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>,
        bgColor: Color,
        fgColor: Color,
        borderColor: Color,
        inlayPadding: Int,
        verticalGap: Int,
        onEdit: () -> Unit
    ): JComponent {
        val hoverPanel = object : JPanel(BorderLayout()) {
            override fun getBackground(): Color = super.getBackground() ?: bgColor
        }.apply {
            isOpaque = true
            background = bgColor
            border = BorderFactory.createCompoundBorder(
                RoundedLineBorder(borderColor, 8, 1),
                JBUI.Borders.empty(inlayPadding)
            )
        }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val headerPanel = JPanel(BorderLayout()).apply { isOpaque = false }

        val statusTag = createTagLabel(comment.status.jsonValue, getStatusColor(comment.status))

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            isVisible = false
        }

        val editButton = InlineIconButton(
            AllIcons.General.Inline_edit,
            AllIcons.General.Inline_edit_hovered
        ).apply {
            actionListener = java.awt.event.ActionListener { onEdit() }
        }
        actionsPanel.add(editButton)

        headerPanel.add(statusTag, BorderLayout.WEST)
        headerPanel.add(actionsPanel, BorderLayout.EAST)

        contentPanel.add(headerPanel)
        contentPanel.add(Box.createVerticalStrut(verticalGap))

        for (entry in comment.thread) {
            val textPane = JTextArea(entry.text).apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                foreground = fgColor
                font = UIUtil.getLabelFont()
                border = JBUI.Borders.emptyBottom(verticalGap)
            }
            contentPanel.add(textPane)
        }

        val bottomActionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(verticalGap)
        }

        if (comment.status != CommentStatus.RESOLVED) {
            bottomActionsPanel.add(ActionLink("Resolve") {
                ReviewService(Path.of(basePath)).updateCommentStatus(comment.id, CommentStatus.RESOLVED)
                refreshCommentInlays(editor, filePath, basePath, commentInlays)
            }.apply { isFocusPainted = false })
        }

        if (comment.status != CommentStatus.FIXED) {
            bottomActionsPanel.add(ActionLink("Fixed") {
                ReviewService(Path.of(basePath)).updateCommentStatus(comment.id, CommentStatus.FIXED)
                refreshCommentInlays(editor, filePath, basePath, commentInlays)
            }.apply { isFocusPainted = false })
        }

        if (comment.status == CommentStatus.RESOLVED || comment.status == CommentStatus.FIXED || comment.status == CommentStatus.WONTFIX) {
            bottomActionsPanel.add(ActionLink("Reopen") {
                ReviewService(Path.of(basePath)).updateCommentStatus(comment.id, CommentStatus.OPEN)
                refreshCommentInlays(editor, filePath, basePath, commentInlays)
            }.apply { isFocusPainted = false })
        }

        if (bottomActionsPanel.componentCount > 0) {
            contentPanel.add(bottomActionsPanel)
        }

        hoverPanel.add(contentPanel, BorderLayout.CENTER)

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

    private fun createEditPanel(
        comment: Comment,
        editor: Editor,
        filePath: String,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>,
        bgColor: Color,
        fgColor: Color,
        borderColor: Color,
        inlayPadding: Int,
        onCancel: () -> Unit
    ): JComponent {
        val reviewService = ReviewService(Path.of(basePath))
        val lastEntry = comment.thread.lastOrNull()
        val originalText = lastEntry?.text ?: ""

        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = bgColor
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
            border = JBUI.Borders.emptyTop(8)
        }

        val hintLabel = JBLabel("Ctrl+Enter to save").apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }

        val saveAndRefresh = {
            val newText = textArea.text.trim()
            if (newText.isNotBlank() && newText != originalText) {
                reviewService.updateCommentText(comment.id, newText)
            }
            refreshCommentInlays(editor, filePath, basePath, commentInlays)
        }

        buttonPanel.add(JButton("Cancel").apply {
            isOpaque = false
            addActionListener { onCancel() }
        })

        buttonPanel.add(JButton("Save").apply {
            putClientProperty("JButton.buttonType", "default")
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

        panel.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentShown(e: java.awt.event.ComponentEvent?) {
                SwingUtilities.invokeLater {
                    textArea.requestFocusInWindow()
                    textArea.selectAll()
                }
            }
        })

        return wrapForFocusIsolation(panel)
    }

    private fun createTagLabel(text: String, color: Color): JComponent {
        return JBLabel(text).apply {
            font = JBFont.small()
            foreground = color
            border = JBUI.Borders.empty(2, 6)
        }.let { label ->
            JPanel(BorderLayout()).apply {
                isOpaque = true
                background = JBColor(
                    Color(color.red, color.green, color.blue, 30),
                    Color(color.red, color.green, color.blue, 50)
                )
                border = JBUI.Borders.empty()
                add(label, BorderLayout.CENTER)
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

    private fun setupGutterComments(
        editor: Editor,
        filePath: String,
        basePath: String,
        project: Project,
        commentInlays: MutableList<Inlay<*>>
    ) {
        var currentHighlighter: RangeHighlighter? = null
        var currentLine: Int = -1

        editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                val line = e.logicalPosition.line

                if (line != currentLine) {
                    currentHighlighter?.let { editor.markupModel.removeHighlighter(it) }
                    currentHighlighter = null
                    currentLine = -1
                }

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
                        gutterIconRenderer = AddCommentGutterIcon(
                            editor, project, filePath, line + 1, basePath, commentInlays
                        )
                    }
                }
            }
        })

        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseExited(e: EditorMouseEvent) {
                currentHighlighter?.let { editor.markupModel.removeHighlighter(it) }
                currentHighlighter = null
                currentLine = -1
            }
        })
    }

    private fun showInlineCommentForm(
        editor: Editor,
        project: Project,
        filePath: String,
        line: Int,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>
    ) {
        val reviewService = ReviewService(Path.of(basePath))

        if (!reviewService.hasActiveReview()) {
            JBPopupFactory.getInstance()
                .createMessage("No active review. Start feature development first.")
                .showInBestPositionFor(editor)
            return
        }

        val editorImpl = editor as? EditorImpl ?: return
        val offset = editor.document.getLineEndOffset(line - 1)

        var formInlay: Inlay<*>? = null

        val onDismiss = {
            formInlay?.dispose()
            Unit
        }

        val formPanel = createNewCommentPanel(editor, filePath, line, basePath, commentInlays, onDismiss)
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
        filePath: String,
        line: Int,
        basePath: String,
        commentInlays: MutableList<Inlay<*>>,
        onDismiss: () -> Unit
    ): JComponent {
        val reviewService = ReviewService(Path.of(basePath))
        val colorsScheme = EditorColorsManager.getInstance().globalScheme
        val bgColor = colorsScheme.defaultBackground
        val fgColor = colorsScheme.defaultForeground
        val borderColor = colorsScheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()

        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = bgColor
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
            border = JBUI.Borders.emptyTop(8)
        }

        val hintLabel = JBLabel("Ctrl+Enter to submit").apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }

        val submitAction = {
            val text = textArea.text.trim()
            if (text.isNotBlank()) {
                reviewService.addComment(filePath, line, text)
                refreshCommentInlays(editor, filePath, basePath, commentInlays)
            }
            onDismiss()
        }

        buttonPanel.add(JButton("Cancel").apply {
            isOpaque = false
            addActionListener { onDismiss() }
        })

        buttonPanel.add(JButton("Add Comment").apply {
            putClientProperty("JButton.buttonType", "default")
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
            border = JBUI.Borders.empty(4, 8, 4, 8)
            add(panel, BorderLayout.CENTER)
        }
        return wrapForFocusIsolation(outerPanel)
    }

    private inner class AddCommentGutterIcon(
        private val editor: Editor,
        private val project: Project,
        private val filePath: String,
        private val line: Int,
        private val basePath: String,
        private val commentInlays: MutableList<Inlay<*>>
    ) : GutterIconRenderer() {

        override fun getIcon(): Icon = AllIcons.General.Add

        override fun getTooltipText(): String = "Add review comment"

        override fun getClickAction(): AnAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                showInlineCommentForm(editor, project, filePath, line, basePath, commentInlays)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AddCommentGutterIcon) return false
            return filePath == other.filePath && line == other.line
        }

        override fun hashCode(): Int = 31 * filePath.hashCode() + line
    }
}
