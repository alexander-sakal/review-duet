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
import javax.swing.SwingConstants

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val basePath: Path by lazy {
        Path.of(project.basePath ?: throw IllegalStateException("No project base path"))
    }

    private val reviewService: ReviewService by lazy { ReviewService(basePath) }
    private val gitService: GitService by lazy { GitService(basePath) }
    private val changesPanel: ChangesPanel by lazy { ChangesPanel(project, gitService) }
    private var commitComboBox: JComboBox<CommitInfo>? = null

    init {
        border = JBUI.Borders.empty(10)
        refresh()
    }

    fun refresh() {
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

        // Header
        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 0, 10, 0)

            add(JBLabel("Round: ${data.currentRound} (vs ${data.baseRef})"))

            val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
            val totalCount = data.comments.size
            add(JBLabel("Progress: $resolvedCount/$totalCount resolved"))
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
        }

        // Action buttons
        val buttonPanel = JBPanel<JBPanel<*>>().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT)
            add(JButton("New Round").apply {
                addActionListener { startNewRound() }
            })
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
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

        // Create baseline tag
        val tagName = "review-r0"
        if (gitService.tagExists(tagName)) {
            val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                project,
                "Tag '$tagName' already exists. Delete and recreate?",
                "Tag Exists",
                com.intellij.openapi.ui.Messages.getQuestionIcon()
            )
            if (result != com.intellij.openapi.ui.Messages.YES) return
            // Note: For MVP, we'll skip the delete step
        }

        if (!gitService.createTagAtCommit(tagName, selectedCommit.sha)) {
            com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to create git tag", "Git Error")
            return
        }

        // Initialize review
        reviewService.initializeReview(tagName)
        refresh()

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            "Review started!\n\nBaseline tag '$tagName' created at commit ${selectedCommit.shortSha}",
            "Review Initialized"
        )
    }

    private fun startNewRound() {
        val data = reviewService.loadReviewData() ?: return

        // Calculate next round number
        val currentNum = data.currentRound.substringAfter("review-r").toIntOrNull() ?: 0
        val newRound = "review-r${currentNum + 1}"

        // Create new tag
        if (!gitService.createTag(newRound)) {
            com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to create git tag '$newRound'", "Git Error")
            return
        }

        // Update review data
        reviewService.startNewRound(newRound)
        refresh()

        val sha = gitService.getCurrentCommitSha() ?: "unknown"
        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            "New review round started!\n\nTag '$newRound' created at commit $sha",
            "New Round"
        )
    }
}
