package com.codereview.local.ui

import com.codereview.local.model.CommentStatus
import com.codereview.local.model.CommitInfo
import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JSeparator
import javax.swing.SwingConstants

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val basePath: Path by lazy {
        Path.of(project.basePath ?: throw IllegalStateException("No project base path"))
    }

    private val changesPanel: ChangesPanel by lazy { ChangesPanel(project, gitService) }
    private var commitComboBox: JComboBox<CommitInfo>? = null
    private var selectedTabIndex: Int = 0

    private var availableRepos: List<Path> = emptyList()
    private var selectedRepoPath: Path = basePath
    private var repoComboBox: JComboBox<String>? = null

    private var reviewService: ReviewService = ReviewService(basePath)
    private var gitService: GitService = GitService(basePath)

    init {
        border = JBUI.Borders.empty(10, 0)
        initRepos()
        refresh()

        // Listen for repository changes (VCS loads async)
        project.messageBus.connect().subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                initRepos()
                refresh()
            }
        )
    }

    private fun initRepos() {
        availableRepos = GitService.discoverRepos(project)
        if (availableRepos.isNotEmpty()) {
            // Keep current selection if still valid, otherwise use first
            if (selectedRepoPath !in availableRepos) {
                selectedRepoPath = availableRepos.first()
            }
            reviewService = ReviewService(selectedRepoPath)
            gitService = GitService(selectedRepoPath)
        }
    }

    private fun onRepoSelected(repoPath: Path) {
        selectedRepoPath = repoPath
        reviewService = ReviewService(repoPath)
        gitService = GitService(repoPath)
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
        // Handle no repos found case
        if (availableRepos.isEmpty()) {
            val errorPanel = JBPanel<JBPanel<*>>().apply {
                layout = GridBagLayout()
                val errorLabel = JBLabel("No git repository found").apply {
                    horizontalAlignment = SwingConstants.CENTER
                }
                add(errorLabel)
            }
            add(errorPanel, BorderLayout.CENTER)
            return
        }

        val centerPanel = JBPanel<JBPanel<*>>().apply {
            layout = GridBagLayout()

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(5, 10, 5, 10)
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }

            // Repo display/selector
            if (availableRepos.size > 1) {
                // Multiple repos: show dropdown selector
                val repoLabel = JBLabel("Repository:")
                add(repoLabel, gbc)

                gbc.gridy = 1
                val repoNames = availableRepos.map { it.fileName.toString() }.toTypedArray()
                repoComboBox = JComboBox(DefaultComboBoxModel(repoNames)).apply {
                    selectedItem = selectedRepoPath.fileName.toString()
                    addActionListener {
                        val selectedName = selectedItem as? String ?: return@addActionListener
                        val repo = availableRepos.find { it.fileName.toString() == selectedName }
                        if (repo != null && repo != selectedRepoPath) {
                            onRepoSelected(repo)
                        }
                    }
                }
                add(repoComboBox, gbc)
                gbc.gridy = 2
                gbc.insets = JBUI.insets(10, 5, 5, 5)
            } else {
                // Single repo: show name as label
                val repoName = selectedRepoPath.fileName.toString()
                val repoLabel = JBLabel("Repository: $repoName").apply {
                    horizontalAlignment = SwingConstants.CENTER
                }
                add(repoLabel, gbc)
                gbc.gridy = 1
                gbc.insets = JBUI.insets(5)
            }

            // Branch display
            val currentBranch = gitService.getCurrentBranch() ?: "unknown"
            val branchLabel = JBLabel("Branch: $currentBranch").apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            add(branchLabel, gbc)

            // Commit selector label
            gbc.gridy++
            gbc.insets = JBUI.insets(15, 5, 5, 5)
            val selectLabel = JBLabel("Review changes starting from:")
            add(selectLabel, gbc)

            // Commit dropdown with refresh button
            gbc.gridy++
            gbc.insets = JBUI.insets(5)
            val commits = gitService.getRecentCommits(25)
            commitComboBox = JComboBox(DefaultComboBoxModel(commits.toTypedArray())).apply {
                if (commits.isNotEmpty()) selectedIndex = 0
            }

            val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
                toolTipText = "Refresh"
                isBorderPainted = false
                isContentAreaFilled = false
                isFocusPainted = false
                margin = JBUI.emptyInsets()
                preferredSize = java.awt.Dimension(24, 24)
                addActionListener { refresh() }
            }

            val commitPanel = JPanel(BorderLayout(5, 0)).apply {
                isOpaque = false
                add(commitComboBox, BorderLayout.CENTER)
                add(refreshButton, BorderLayout.EAST)
            }
            add(commitPanel, gbc)

            // Start button
            gbc.gridy++
            val button = JButton("Start Review").apply {
                addActionListener { startReview() }
            }
            add(button, gbc)
        }

        add(centerPanel, BorderLayout.CENTER)
    }

    private fun showActiveReviewPanel() {
        val data = reviewService.loadReviewData() ?: return

        // Header - show base commit and progress
        val shortCommit = data.baseCommit.take(7)
        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 10, 4, 10)

            add(JBLabel("Reviewing changes since: $shortCommit"))

            val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
            val totalCount = data.comments.size
            add(JBLabel("Progress: $resolvedCount/$totalCount resolved"))
        }

        // Comment list panel - sort: fixed first (user reviews), open (agent's turn), resolved last
        val sortedComments = data.comments.sortedWith(compareBy { comment ->
            when (comment.status) {
                CommentStatus.FIXED -> 0    // User needs to review these
                CommentStatus.OPEN -> 1     // Waiting for agent
                CommentStatus.RESOLVED -> 2
            }
        })

        val commentsContent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val commentList = CommentListPanel(
                project,
                sortedComments,
                onCommentSelected = { comment -> showCommentDetails(comment) },
                onStatusChange = { comment, status ->
                    reviewService.updateCommentStatus(comment.id, status)
                    refresh()
                }
            )
            add(commentList, BorderLayout.CENTER)
        }

        // Tabbed pane
        val tabbedPane = JBTabbedPane().apply {
            putClientProperty("JBTabbedPane.tabAreaInsets", JBUI.insets(0))
            border = JBUI.Borders.emptyTop(4)
            addTab("Comments", commentsContent)
            addTab("Changes", changesPanel)
            selectedIndex = selectedTabIndex.coerceIn(0, tabCount - 1)
        }

        // Action buttons with separator above
        val buttonPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JSeparator(), BorderLayout.NORTH)
            val buttonsContainer = JBPanel<JBPanel<*>>().apply {
                layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)
                border = JBUI.Borders.empty(8, 0, 0, 0)
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
            onStatusChange = { status ->
                reviewService.updateCommentStatus(comment.id, status)
                refresh()
            },
            onDelete = {
                reviewService.deleteComment(comment.id)
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

        // Get parent of selected commit as the baseline
        // This way "review from commit X" means X is included in the review
        val parentSha = gitService.getParentCommitSha(selectedCommit.sha)
        if (parentSha == null) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                "Cannot start review from the initial commit (no parent).",
                "Invalid Selection"
            )
            return
        }

        reviewService.initializeReview(parentSha)
        refresh()
    }

    private fun endReview() {
        val branch = gitService.getCurrentBranch() ?: "unknown"
        val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            "End this review session? The review file for branch '$branch' will be deleted.",
            "End Review",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        )
        if (result != com.intellij.openapi.ui.Messages.YES) return

        // Delete review file for current branch
        val reviewFile = basePath.resolve(".review-duet").resolve("$branch.json").toFile()
        if (reviewFile.exists()) {
            reviewFile.delete()
        }
        // Clean up empty directory
        val reviewDir = basePath.resolve(".review-duet").toFile()
        if (reviewDir.exists() && reviewDir.listFiles()?.isEmpty() == true) {
            reviewDir.delete()
        }
        refresh()
    }
}
