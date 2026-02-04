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
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.ListCellRenderer
import javax.swing.JList
import java.awt.Component
import com.intellij.ui.JBColor

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val basePath: Path by lazy {
        Path.of(project.basePath ?: throw IllegalStateException("No project base path"))
    }

    private var commitComboBox: JComboBox<CommitInfo>? = null
    private var selectedTabIndex: Int = 0

    private var availableRepos: List<Path> = emptyList()
    private var selectedRepoPath: Path = basePath
    private var repoComboBox: JComboBox<String>? = null

    private var reviewService: ReviewService = ReviewService(basePath)
    private var gitService: GitService = GitService(basePath)
    private var changesPanel: ChangesPanel = ChangesPanel(project, gitService)

    /**
     * Returns the path to the currently active review file, or null if no review is active.
     * Used by FileWatcher to only refresh when the active review file changes.
     */
    fun getActiveReviewFilePath(): String? {
        val branch = gitService.getCurrentBranch() ?: return null
        return selectedRepoPath.resolve(".review-duet").resolve("$branch.json").toString()
    }

    init {
        border = JBUI.Borders.empty(10, 0)
        initRepos()
        refresh()

        // Listen for repository changes (VCS loads async)
        val connection = project.messageBus.connect()
        connection.subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                initRepos()
                refresh()
            }
        )
        // Listen for when repositories are first discovered (initial scan)
        connection.subscribe(
            VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
            VcsRepositoryMappingListener {
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
            changesPanel = ChangesPanel(project, gitService)
        }
    }

    private fun onRepoSelected(repoPath: Path) {
        selectedRepoPath = repoPath
        reviewService = ReviewService(repoPath)
        gitService = GitService(repoPath)
        changesPanel = ChangesPanel(project, gitService)
        refresh()
    }

    fun refresh() {
        // Save current tab before removing components
        components.filterIsInstance<JBTabbedPane>().firstOrNull()?.let {
            selectedTabIndex = it.selectedIndex
        }

        // Validate data BEFORE destroying UI to prevent empty panel on transient errors
        val hasActiveReview = reviewService.hasActiveReview()
        val reviewData = if (hasActiveReview) reviewService.reloadReviewData() else null

        // If we have an active review but can't load data, keep current UI (transient error)
        if (hasActiveReview && reviewData == null) {
            return
        }

        removeAll()

        if (hasActiveReview && reviewData != null) {
            changesPanel.refresh()
            showActiveReviewPanel(reviewData)
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
                val gbc = GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    insets = JBUI.insets(5)
                }
                val errorLabel = JBLabel("No git repository found")
                add(errorLabel, gbc)

                gbc.gridy = 1
                val refreshButton = JButton("Refresh").apply {
                    addActionListener {
                        initRepos()
                        refresh()
                    }
                }
                add(refreshButton, gbc)
            }
            add(errorPanel, BorderLayout.CENTER)
            return
        }

        // Content panel with fixed max width
        val contentPanel = JBPanel<JBPanel<*>>().apply {
            layout = GridBagLayout()

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(5, 0, 5, 0)
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
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
                gbc.insets = JBUI.insets(10, 0, 5, 0)
            } else {
                // Single repo: show name as label
                val repoName = selectedRepoPath.fileName.toString()
                val repoLabel = JBLabel("Repository: $repoName")
                add(repoLabel, gbc)
                gbc.gridy = 1
                gbc.insets = JBUI.insets(5, 0, 5, 0)
            }

            // Branch display
            val currentBranch = gitService.getCurrentBranch() ?: "unknown"
            val branchLabel = JBLabel("Branch: $currentBranch")
            add(branchLabel, gbc)

            // Commit selector label
            gbc.gridy++
            gbc.insets = JBUI.insets(15, 0, 5, 0)
            val selectLabel = JBLabel("Review changes starting from:")
            add(selectLabel, gbc)

            // Commit dropdown
            gbc.gridy++
            gbc.insets = JBUI.insets(5, 0, 5, 0)
            val commits = gitService.getRecentCommits(100)
            val baseBranch = gitService.getBaseBranch()
            val newCommitShas = baseBranch?.let { gitService.getNewCommitShas(it) } ?: emptySet()
            commitComboBox = JComboBox(DefaultComboBoxModel(commits.toTypedArray())).apply {
                if (commits.isNotEmpty()) selectedIndex = 0
                renderer = CommitCellRenderer(newCommitShas)
            }
            add(commitComboBox, gbc)

            // Start button
            gbc.gridy++
            val button = JButton("Start Review").apply {
                addActionListener { startReview() }
            }
            add(button, gbc)
        }

        // Wrapper with horizontal BoxLayout to center content with max-width
        val wrapperPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)

            val horizontalBox = Box.createHorizontalBox().apply {
                add(Box.createHorizontalGlue())
                add(object : JBPanel<JBPanel<*>>(BorderLayout()) {
                    init {
                        add(contentPanel, BorderLayout.CENTER)
                    }
                    override fun getPreferredSize(): java.awt.Dimension {
                        val pref = super.getPreferredSize()
                        return java.awt.Dimension(minOf(pref.width, 350), pref.height)
                    }
                    override fun getMaximumSize(): java.awt.Dimension {
                        return java.awt.Dimension(350, Int.MAX_VALUE)
                    }
                })
                add(Box.createHorizontalGlue())
            }

            val verticalBox = Box.createVerticalBox().apply {
                add(horizontalBox)
                add(Box.createVerticalGlue())
            }

            add(verticalBox, BorderLayout.CENTER)
        }

        add(wrapperPanel, BorderLayout.CENTER)
    }

    private fun showActiveReviewPanel(data: com.codereview.local.model.ReviewData) {
        // Header - show repo, branch, base commit and progress
        val commitInfo = gitService.getCommitInfo(data.baseCommit)
        val commitDisplay = if (commitInfo != null) {
            "${commitInfo.shortSha} ${commitInfo.message}"
        } else {
            data.baseCommit.take(7)
        }
        val currentBranch = gitService.getCurrentBranch() ?: "unknown"
        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 10, 4, 10)

            // Repo selector or label
            if (availableRepos.size > 1) {
                val repoRow = JPanel(BorderLayout(5, 0)).apply {
                    isOpaque = false
                    alignmentX = LEFT_ALIGNMENT
                    maximumSize = java.awt.Dimension(Int.MAX_VALUE, 24)

                    add(JBLabel("Repository:"), BorderLayout.WEST)
                    val repoNames = availableRepos.map { it.fileName.toString() }.toTypedArray()
                    val repoSelector = JComboBox(DefaultComboBoxModel(repoNames)).apply {
                        selectedItem = selectedRepoPath.fileName.toString()
                        addActionListener {
                            val selectedName = selectedItem as? String ?: return@addActionListener
                            val repo = availableRepos.find { it.fileName.toString() == selectedName }
                            if (repo != null && repo != selectedRepoPath) {
                                onRepoSelected(repo)
                            }
                        }
                    }
                    add(repoSelector, BorderLayout.CENTER)
                }
                add(repoRow)
                add(Box.createVerticalStrut(4))
            } else {
                val repoName = selectedRepoPath.fileName.toString()
                add(JBLabel("Repository: $repoName").apply { alignmentX = LEFT_ALIGNMENT })
            }

            // Branch display
            add(JBLabel("Branch: $currentBranch").apply { alignmentX = LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(4))

            // Review info
            add(JBLabel("Reviewing changes since: $commitDisplay").apply { alignmentX = LEFT_ALIGNMENT })

            val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
            val totalCount = data.comments.size
            add(JBLabel("Progress: $resolvedCount/$totalCount resolved").apply { alignmentX = LEFT_ALIGNMENT })
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
        val reviewFile = selectedRepoPath.resolve(".review-duet").resolve("$branch.json").toFile()
        if (reviewFile.exists()) {
            reviewFile.delete()
        }
        // Clean up empty directory
        val reviewDir = selectedRepoPath.resolve(".review-duet").toFile()
        if (reviewDir.exists() && reviewDir.listFiles()?.isEmpty() == true) {
            reviewDir.delete()
        }
        refresh()
    }
}

/**
 * Custom cell renderer that highlights commits not present in the base branch.
 */
private class CommitCellRenderer(
    private val newCommitShas: Set<String>
) : ListCellRenderer<CommitInfo> {

    private val defaultRenderer = javax.swing.DefaultListCellRenderer()

    override fun getListCellRendererComponent(
        list: JList<out CommitInfo>?,
        value: CommitInfo?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val component = defaultRenderer.getListCellRendererComponent(
            list, value?.displayText ?: "", index, isSelected, cellHasFocus
        )

        if (component is javax.swing.JLabel && value != null) {
            val isInBaseBranch = value.sha !in newCommitShas
            if (isInBaseBranch && !isSelected) {
                component.foreground = JBColor.GRAY
            }
        }

        return component
    }
}
