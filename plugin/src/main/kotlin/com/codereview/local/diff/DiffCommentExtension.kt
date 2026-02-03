package com.codereview.local.diff

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
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

        val editor = viewer.editor2
        val title = request.title ?: return

        // Extract file path from title
        val filePath = extractFilePath(title)

        // Find the repo that contains this file
        val repos = GitService.discoverRepos(project)
        val repoRoot = repos.find { repo ->
            repo.resolve(filePath).toFile().exists()
        } ?: return
        val basePath = repoRoot.toString()

        val commentInlays = mutableListOf<Inlay<*>>()

        setupGutterComments(editor, filePath, basePath, project, commentInlays)

        // Add reviewed action to context
        setupReviewedAction(viewer, context, filePath, basePath)

        // Add custom review toolbar only for comment review diffs
        val isCommentReview = request.getUserData(com.codereview.local.ui.ChangesPanel.IS_COMMENT_REVIEW_KEY) == true
        val commentId = request.getUserData(com.codereview.local.ui.ChangesPanel.COMMENT_ID_KEY)
        if (isCommentReview && commentId != null) {
            SwingUtilities.invokeLater {
                addReviewToolbar(viewer, project, filePath, basePath, commentId)
            }
        }

        // Display existing comments
        SwingUtilities.invokeLater {
            displayComments(editor, filePath, basePath, commentInlays)
        }
    }

    private fun addReviewToolbar(viewer: TwosideTextDiffViewer, project: Project, filePath: String, basePath: String, commentId: Int) {
        val viewerComponent = viewer.component
        val parent = viewerComponent.parent as? JComponent ?: return

        // Create review toolbar panel
        val toolbarPanel = createReviewToolbarPanel(project, filePath, basePath, commentId) {
            // Callback to close the diff window after resolve
            SwingUtilities.getWindowAncestor(parent)?.dispose()
        }

        // Find the viewer in parent and wrap it
        val parentLayout = parent.layout
        if (parentLayout is BorderLayout) {
            parent.remove(viewerComponent)

            val wrapper = JPanel(BorderLayout()).apply {
                add(toolbarPanel, BorderLayout.NORTH)
                add(viewerComponent, BorderLayout.CENTER)
            }

            parent.add(wrapper, BorderLayout.CENTER)
            parent.revalidate()
            parent.repaint()
        }
    }

    private fun createReviewToolbarPanel(project: Project, filePath: String, basePath: String, commentId: Int, onResolve: () -> Unit): JComponent {
        val reviewService = ReviewService(Path.of(basePath))
        val reviewData = reviewService.loadReviewData()
        val comment = reviewData?.getComment(commentId)

        val separatorColor = JBColor(Color(0, 0, 0, 25), Color(255, 255, 255, 25))
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, separatorColor),  // Top and bottom, very light
                JBUI.Borders.empty(4, 8, 4, 2)
            )
            background = UIUtil.getPanelBackground()
        }

        // Left side: Comment info - use GridBagLayout for vertical centering
        val infoPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
        }

        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 0, 8)
        }

        infoPanel.add(JBLabel("#$commentId").apply {
            font = JBFont.regular().asBold()
        }, gbc)

        comment?.let { c ->
            val statusColor = getStatusColor(c.status)
            infoPanel.add(createTagLabel(c.status.jsonValue, statusColor), gbc)

            c.firstUserMessage?.let { msg ->
                // Use a label that truncates based on available width
                val singleLine = msg.replace("\n", " ").replace("\r", " ").replace("  ", " ")

                gbc.weightx = 1.0
                gbc.fill = GridBagConstraints.HORIZONTAL
                infoPanel.add(createClickableCommentLabel(singleLine, msg), gbc)
            }
        }

        // Right side: Actions - use GridBagLayout for vertical centering
        val actionsPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
        }

        val actionGbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.CENTER
            insets = Insets(0, 4, 0, 4)
        }

        // Get list of fixed comments for navigation
        val fixedComments = reviewData?.comments
            ?.filter { it.status == CommentStatus.FIXED }
            ?.sortedBy { it.id }
            ?: emptyList()

        // Separator before actions
        actionsPanel.add(createVerticalSeparator(), actionGbc)

        // Show remaining count - always visible
        val remainingLabel = JBLabel("${fixedComments.size} remaining").apply {
            foreground = JBColor.GRAY
            font = JBFont.small()
        }
        actionsPanel.add(remainingLabel, actionGbc)

        // Create Resolve and Next buttons - both visible simultaneously
        actionGbc.insets = Insets(0, 4, 0, 0)

        // Helper to update button icon based on enabled state
        fun updateButtonIcon(button: JButton, icon: Icon) {
            button.icon = if (button.isEnabled) icon else IconLoader.getDisabledIcon(icon)
        }

        val resolveIcon = AllIcons.Actions.Checked
        val nextIcon = AllIcons.Actions.NextOccurence

        lateinit var nextButton: JButton

        val resolveButton = JButton("Resolve").apply {
            icon = if (comment?.status == CommentStatus.FIXED) resolveIcon else IconLoader.getDisabledIcon(resolveIcon)
            toolTipText = "Mark comment as resolved"
            isEnabled = comment?.status == CommentStatus.FIXED
            addActionListener {
                reviewService.updateCommentStatus(commentId, CommentStatus.RESOLVED)

                // Update remaining count
                val updatedData = reviewService.loadReviewData()
                val remainingComments = updatedData?.comments
                    ?.filter { it.status == CommentStatus.FIXED }
                    ?: emptyList()

                // Update UI
                isEnabled = false
                icon = IconLoader.getDisabledIcon(resolveIcon)
                remainingLabel.text = "${remainingComments.size} remaining"

                // Close window if no more comments, otherwise disable next
                if (remainingComments.isEmpty()) {
                    onResolve()
                } else {
                    // Keep next enabled for navigation to other comments
                }
            }
        }

        nextButton = JButton("Next").apply {
            // Enable if there are other fixed comments besides the current one
            val hasNext = fixedComments.size > 1 || (fixedComments.size == 1 && fixedComments.first().id != commentId)
            icon = if (hasNext) nextIcon else IconLoader.getDisabledIcon(nextIcon)
            toolTipText = "Go to next comment"
            isEnabled = hasNext
            addActionListener {
                val updatedData = reviewService.loadReviewData()
                val remainingComments = updatedData?.comments
                    ?.filter { it.status == CommentStatus.FIXED }
                    ?.sortedBy { it.id }
                    ?: emptyList()

                if (remainingComments.isNotEmpty()) {
                    // Find next comment (after current, or wrap to first)
                    val currentIndex = remainingComments.indexOfFirst { it.id == commentId }
                    val nextComment = if (currentIndex >= 0 && currentIndex < remainingComments.size - 1) {
                        remainingComments[currentIndex + 1]
                    } else {
                        remainingComments.first()
                    }

                    nextComment.resolveCommit?.let { commit ->
                        val window = SwingUtilities.getWindowAncestor(this)
                        val bounds = window?.bounds
                        com.codereview.local.ui.ChangesPanel.openDiffForSingleCommit(project, commit, nextComment.file, nextComment.id, bounds)
                        window?.dispose()
                    }
                }
            }
        }

        actionsPanel.add(resolveButton, actionGbc)
        actionsPanel.add(nextButton, actionGbc)

        panel.add(infoPanel, BorderLayout.CENTER)
        panel.add(actionsPanel, BorderLayout.EAST)

        return panel
    }

    private fun setupReviewedAction(viewer: TwosideTextDiffViewer, context: DiffContext, filePath: String, basePath: String) {
        // Store the file path in the context for the action to access
        context.putUserData(FILE_PATH_KEY, filePath)
        context.putUserData(BASE_PATH_KEY, basePath)
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

        // Only show open comments in diff view - these are freshly created, position is correct
        // Once Claude processes comments, they may be at wrong positions - manage via Comments panel
        val fileComments = reviewData.comments.filter {
            it.file == filePath && it.status == CommentStatus.OPEN
        }

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

        val inlayPadding = 6
        val verticalGap = 4

        val cardLayout = CardLayout()
        var currentCard: JComponent? = null
        val cardPanel = object : JPanel(cardLayout) {
            override fun getPreferredSize(): Dimension {
                // Use current card's size, not max of all cards
                return currentCard?.preferredSize ?: super.getPreferredSize()
            }
        }.apply { isOpaque = false }

        var displayPanel: JComponent? = null
        var editPanel: JComponent? = null

        displayPanel = createDisplayPanel(
            comment, editor, filePath, basePath, commentInlays,
            bgColor, fgColor, borderColor, inlayPadding, verticalGap
        ) {
            currentCard = editPanel
            cardLayout.show(cardPanel, "edit")
            cardPanel.revalidate()
            cardPanel.repaint()
        }

        editPanel = createEditPanel(
            comment, editor, filePath, basePath, commentInlays,
            bgColor, fgColor, borderColor, inlayPadding
        ) {
            currentCard = displayPanel
            cardLayout.show(cardPanel, "display")
            cardPanel.revalidate()
            cardPanel.repaint()
        }

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

        val statusTag = createTagLabel(comment.status.jsonValue, getStatusColor(comment.status))

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
            actionListener = java.awt.event.ActionListener {
                val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                    "Delete this comment?",
                    "Delete Comment",
                    com.intellij.openapi.ui.Messages.getQuestionIcon()
                )
                if (result == com.intellij.openapi.ui.Messages.YES) {
                    ReviewService(Path.of(basePath)).deleteComment(comment.id)
                    refreshCommentInlays(editor, filePath, basePath, commentInlays)
                }
            }
        }
        actionsPanel.add(deleteButton)

        // Format date from first thread entry
        val dateLabel = comment.thread.firstOrNull()?.at?.let { timestamp ->
            try {
                val instant = java.time.Instant.parse(timestamp)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                JBLabel(formatter.format(instant)).apply {
                    foreground = JBColor.GRAY
                    font = JBFont.small()
                }
            } catch (e: Exception) {
                null
            }
        }

        // Wrap in FlowLayout to prevent BorderLayout.WEST from stretching vertically
        val tagWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty()
            dateLabel?.let { add(it) }
            add(statusTag)
        }
        headerPanel.add(tagWrapper, BorderLayout.WEST)
        headerPanel.add(actionsPanel, BorderLayout.EAST)

        contentPanel.add(headerPanel)
        contentPanel.add(Box.createVerticalStrut(4))

        for (entry in comment.thread) {
            val textPane = object : JTextArea(entry.text) {
                override fun getMaximumSize(): Dimension {
                    val pref = preferredSize
                    return Dimension(Int.MAX_VALUE, pref.height)
                }
            }.apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                foreground = fgColor
                font = UIUtil.getLabelFont()
                border = JBUI.Borders.empty()
                cursor = Cursor.getDefaultCursor()
            }
            contentPanel.add(textPane)
        }

        // No bottom actions - only open comments shown in diff view, resolve via review toolbar

        // Push content to top
        contentPanel.add(Box.createVerticalGlue())

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
                reviewService.updateCommentText(comment.id, newText)
            }
            refreshCommentInlays(editor, filePath, basePath, commentInlays)
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
        val bgColor = JBColor(
            Color(color.red, color.green, color.blue, 30),
            Color(color.red, color.green, color.blue, 50)
        )
        val font = JBFont.small()
        val hPad = 6
        val vPad = 2

        return object : JComponent() {
            init {
                isOpaque = false
            }

            override fun getPreferredSize(): Dimension {
                val fm = getFontMetrics(font)
                val textWidth = fm.stringWidth(text)
                val textHeight = fm.height
                return Dimension(textWidth + hPad * 2, textHeight + vPad * 2)
            }

            override fun getMinimumSize(): Dimension = preferredSize
            override fun getMaximumSize(): Dimension = preferredSize

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                // Draw rounded background
                g2.color = bgColor
                g2.fillRoundRect(0, 0, width, height, 8, 8)

                // Draw text
                g2.font = font
                g2.color = color
                val fm = g2.fontMetrics
                val x = hPad
                val y = (height + fm.ascent - fm.descent) / 2
                g2.drawString(text, x, y)

                g2.dispose()
            }
        }
    }

    private fun createClickableCommentLabel(displayText: String, fullText: String): JComponent {
        val label = object : JBLabel(displayText) {
            private var lastWidth = -1
            private var cachedText = displayText

            init {
                foreground = JBColor.GRAY
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        showCommentPopup(fullText, e.component)
                    }

                    override fun mouseEntered(e: java.awt.event.MouseEvent) {
                        foreground = JBColor.BLUE
                    }

                    override fun mouseExited(e: java.awt.event.MouseEvent) {
                        foreground = JBColor.GRAY
                    }
                })
            }

            override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
                super.setBounds(x, y, width, height)
                // Recalculate truncation only when width changes
                if (width != lastWidth && width > 0) {
                    lastWidth = width
                    cachedText = truncateToFit(displayText, width)
                    text = cachedText
                }
            }

            private fun truncateToFit(text: String, availableWidth: Int): String {
                val fm = getFontMetrics(font)
                val fullWidth = fm.stringWidth(text)
                if (fullWidth <= availableWidth) return text

                val ellipsis = "..."
                val ellipsisWidth = fm.stringWidth(ellipsis)
                var truncated = text

                while (fm.stringWidth(truncated) + ellipsisWidth > availableWidth && truncated.isNotEmpty()) {
                    truncated = truncated.dropLast(1)
                }

                return truncated + ellipsis
            }
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(label, BorderLayout.CENTER)
        }
    }

    private fun showCommentPopup(text: String, component: java.awt.Component) {
        val textArea = JBTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = UIUtil.getToolTipBackground()
            foreground = UIUtil.getToolTipForeground()
            border = JBUI.Borders.empty(12)
            columns = 60
            rows = minOf(text.lines().size + 1, 12)
        }

        val scrollPane = JBScrollPane(textArea).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(500, minOf(textArea.preferredSize.height + 24, 250))
        }

        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, textArea)
            .setRequestFocus(false)
            .setFocusable(false)
            .setMovable(false)
            .setResizable(false)
            .createPopup()
            .showUnderneathOf(component)
    }

    private fun createVerticalSeparator(): JComponent {
        return object : JComponent() {
            init {
                preferredSize = Dimension(1, 16)
                minimumSize = preferredSize
            }

            override fun paintComponent(g: Graphics) {
                g.color = JBColor(Color(0, 0, 0, 60), Color(255, 255, 255, 60))
                g.fillRect(0, 2, 1, height - 4)
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
                reviewService.addComment(filePath, line, text)
                refreshCommentInlays(editor, filePath, basePath, commentInlays)
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
