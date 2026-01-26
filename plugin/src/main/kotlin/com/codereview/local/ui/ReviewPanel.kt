package com.codereview.local.ui

import com.codereview.local.model.CommentStatus
import com.codereview.local.model.CommitInfo
import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Path
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JSeparator
import javax.swing.SwingConstants

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val basePath: Path by lazy {
        Path.of(project.basePath ?: throw IllegalStateException("No project base path"))
    }

    private val reviewService: ReviewService by lazy { ReviewService(basePath) }
    private val gitService: GitService by lazy { GitService(basePath) }
    private val changesPanel: ChangesPanel by lazy { ChangesPanel(project, gitService) }
    private var commitComboBox: JComboBox<CommitInfo>? = null
    private var selectedTabIndex: Int = 0

    init {
        border = JBUI.Borders.empty(10, 0)
        refresh()
    }

    fun refresh() {
        // Save current tab before removing components
        components.filterIsInstance<JBTabbedPane>().firstOrNull()?.let {
            selectedTabIndex = it.selectedIndex
        }

        removeAll()

        if (reviewService.hasActiveReview()) {
            changesPanel.refresh()
            showActiveReviewPanel()
        } else {
            showNoReviewPanel()
        }

        revalidate()
        repaint()
    }

    private fun showNoReviewPanel() {
        val centerPanel = JBPanel<JBPanel<*>>().apply {
            layout = GridBagLayout()

            val label = JBLabel("No active review").apply {
                horizontalAlignment = SwingConstants.CENTER
            }

            val selectLabel = JBLabel("Select baseline commit:")

            val commits = gitService.getRecentCommits(25)
            commitComboBox = JComboBox(DefaultComboBoxModel(commits.toTypedArray())).apply {
                if (commits.isNotEmpty()) selectedIndex = 0
            }

            val button = JButton("Start Review").apply {
                addActionListener { startReview() }
            }

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(5)
                fill = GridBagConstraints.HORIZONTAL
            }
            add(label, gbc)

            gbc.gridy = 1
            gbc.insets = JBUI.insets(15, 5, 5, 5)
            add(selectLabel, gbc)

            gbc.gridy = 2
            gbc.insets = JBUI.insets(5)
            add(commitComboBox, gbc)

            gbc.gridy = 3
            add(button, gbc)
        }

        add(centerPanel, BorderLayout.CENTER)
    }

    private fun showActiveReviewPanel() {
        val data = reviewService.loadReviewData() ?: return

        // Header - show base commit and progress with separator below
        val shortCommit = data.baseCommit.take(7)
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val infoPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(0, 10, 8, 10)

                add(JBLabel("Reviewing changes since: $shortCommit"))

                val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
                val totalCount = data.comments.size
                add(JBLabel("Progress: $resolvedCount/$totalCount resolved"))
            }
            add(infoPanel, BorderLayout.CENTER)
            add(JSeparator(), BorderLayout.SOUTH)
        }

        // Comment list panel
        val commentsContent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val commentList = CommentListPanel(data.comments) { comment ->
                showCommentDetails(comment)
            }
            add(commentList, BorderLayout.CENTER)
        }

        // Tabbed pane
        val tabbedPane = JBTabbedPane().apply {
            addTab("Comments", commentsContent)
            addTab("Changes", changesPanel)
            selectedIndex = selectedTabIndex.coerceIn(0, tabCount - 1)
        }

        // Action buttons with separator above
        val buttonPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JSeparator(), BorderLayout.NORTH)
            val buttonsContainer = JBPanel<JBPanel<*>>().apply {
                layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 8)
                add(JButton("Refresh").apply {
                    addActionListener { refresh() }
                })
                add(JButton("End Review").apply {
                    addActionListener { endReview() }
                })
            }
            add(buttonsContainer, BorderLayout.CENTER)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun showCommentDetails(comment: com.codereview.local.model.Comment) {
        val popup = CommentPopup(
            comment = comment,
            onReply = { text ->
                reviewService.addReply(comment.id, text)
                refresh()
            },
            onStatusChange = { status ->
                reviewService.updateCommentStatus(comment.id, status)
                refresh()
            }
        )
        popup.show()
    }

    private fun startReview() {
        val selectedCommit = commitComboBox?.selectedItem as? CommitInfo
        if (selectedCommit == null) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                "Please select a commit to start the review from.",
                "No Commit Selected"
            )
            return
        }

        // Initialize review with the selected commit SHA
        reviewService.initializeReview(selectedCommit.sha)
        refresh()

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            "Review started!\n\nReviewing changes since commit ${selectedCommit.shortSha}",
            "Review Initialized"
        )
    }

    private fun endReview() {
        val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            "End this review session? The .review folder will be deleted.",
            "End Review",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        )
        if (result != com.intellij.openapi.ui.Messages.YES) return

        // Delete .review folder
        val reviewDir = basePath.resolve(".review").toFile()
        if (reviewDir.exists()) {
            reviewDir.deleteRecursively()
        }
        refresh()
    }
}
