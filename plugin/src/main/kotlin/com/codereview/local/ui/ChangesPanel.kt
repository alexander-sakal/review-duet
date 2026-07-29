package com.codereview.local.ui

import com.codereview.local.model.Review
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitChangeUtils
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ChangesPanel(
    project: Project,
    private val review: Review
) : JPanel(BorderLayout()) {

    companion object {
        private val LOG = logger<ChangesPanel>()

        val IS_COMMENT_REVIEW_KEY = Key.create<Boolean>("CodeReview.IsCommentReview")
        val COMMENT_ID_KEY = Key.create<Int>("CodeReview.CommentId")
        val REVIEW_KEY = Key.create<Review>("CodeReview.Review")

        /** Repo-relative path of the diffed file, for the comment gutter and review actions. */
        val FILE_PATH_KEY = Key.create<String>("CodeReview.DiffFilePath")

        /**
         * Open diff view showing changes in a single commit (commit^ to commit)
         */
        fun openDiffForSingleCommit(project: Project, review: Review, commitSha: String, filePath: String? = null, commentId: Int? = null, windowBounds: Rectangle? = null) {
            val parentCommit = review.gitService.getParentCommitSha(commitSha) ?: return
            openDiffBetweenCommits(project, review, parentCommit, commitSha, filePath, commentId = commentId, windowBounds = windowBounds)
        }

        /**
         * Open diff view showing changes between two commits.
         *
         * Changes are computed by git4idea, so renames are detected and every file of the commit is
         * represented; ordering matches the Changes tree, so next/previous-file navigation agrees
         * with it.
         */
        fun openDiffBetweenCommits(project: Project, review: Review, fromCommit: String, toCommit: String, filePath: String? = null, commentId: Int? = null, windowBounds: Rectangle? = null) {
            object : Task.Backgroundable(project, "Loading Changes", true) {
                private var producers: List<ChangeDiffRequestChain.Producer> = emptyList()
                private var startIndex = 0

                override fun run(indicator: ProgressIndicator) {
                    val root = ReviewGit.repository(project, review)?.root ?: return

                    val changes = try {
                        GitChangeUtils.getDiff(project, root, fromCommit, toCommit, null)
                    } catch (e: VcsException) {
                        LOG.warn("Cannot compute changes for $fromCommit..$toCommit", e)
                        return
                    }

                    val ordered = changes.sortedWith(ChangesComparator.getInstance(false))
                    val relativePaths = ordered.map { VcsFileUtil.relativePath(root, ChangesUtil.getFilePath(it)) }

                    producers = ordered.mapIndexedNotNull { index, change ->
                        ChangeDiffRequestProducer.create(project, change)?.let {
                            ReviewChangeDiffProducer(it, review, relativePaths[index], commentId)
                        }
                    }
                    startIndex = if (filePath != null) {
                        relativePaths.indexOf(filePath).coerceAtLeast(0)
                    } else 0
                }

                override fun onSuccess() {
                    if (producers.isEmpty()) return

                    DiffManager.getInstance().showDiff(
                        project,
                        ChangeDiffRequestChain(producers, startIndex),
                        DiffDialogHints.FRAME
                    )

                    if (windowBounds != null) {
                        SwingUtilities.invokeLater {
                            // Find the most recently opened diff window
                            Window.getWindows()
                                .filterIsInstance<JFrame>()
                                .lastOrNull { it.isVisible && it.title?.contains(toCommit.take(7)) == true }
                                ?.bounds = windowBounds
                        }
                    }
                }
            }.queue()
        }
    }

    private val browser = ReviewChangesBrowser(project, review)

    init {
        // The tree had no border before it moved onto the platform browser
        browser.hideViewerBorder()
        browser.addToolbarSeparator()
        browser.addToolbarAction(ShowLastDiffAction())

        add(browser, BorderLayout.CENTER)
    }

    /** Cancels the async tree's coroutine scope; call when this panel is discarded. */
    fun shutdown() {
        browser.shutdown()
    }

    fun refresh() {
        border = JBUI.Borders.empty()
        browser.reload()
        revalidate()
        repaint()
    }

    private inner class ShowLastDiffAction : AnAction(
        "Show Last Diff",
        "Bring last diff window to front",
        AllIcons.Actions.Preview
    ) {
        override fun actionPerformed(e: AnActionEvent) = browser.focusLastDiffWindow()

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = browser.hasLastDiffWindow()
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }
}
