package com.codereview.local.ui

import com.codereview.local.model.Review
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.AsyncChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitChangeUtils
import java.awt.Window
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

/**
 * Changes view for the review branch, backed by the platform change browser: tree ordering,
 * directory compaction, grouping and diff navigation order are the IDE's own.
 *
 * Changes come from [GitChangeUtils.getDiff], the IDE's own commit-diff computation, so renames are
 * detected instead of collapsing a rename into its vanished old path.
 *
 * Both sides are git blobs, which keeps the diff read-only: [com.codereview.local.model.Comment]
 * anchors to a line number with no text anchor, so an editable right side would shift code out from
 * under existing comments.
 *
 * git runs inside the async tree model's background rebuild, so refreshing never queries git on the
 * EDT.
 */
class ReviewChangesBrowser(
    project: Project,
    private val review: Review
) : AsyncChangesBrowserBase(project, false, false) {

    private var lastDiffWindow: WeakReference<Window>? = null

    override val changesTreeModel: AsyncChangesTreeModel
        get() = SimpleAsyncChangesTreeModel.create { grouping ->
            TreeModelBuilder.buildFromChanges(myProject, grouping, loadChanges(), null)
        }

    /** Recomputes changes from git and rebuilds the tree, both in the background. */
    fun reload() {
        viewer.rebuildTree()
    }

    fun hasLastDiffWindow(): Boolean = lastDiffWindow?.get()?.isDisplayable == true

    fun focusLastDiffWindow() {
        val window = lastDiffWindow?.get()?.takeIf { it.isDisplayable } ?: return
        window.toFront()
        window.requestFocus()
    }

    override fun showDiff() {
        val windowsBefore = Window.getWindows().toSet()
        super.showDiff()
        SwingUtilities.invokeLater {
            Window.getWindows().firstOrNull { it.isVisible && it !in windowsBefore }
                ?.let { lastDiffWindow = WeakReference(it) }
        }
    }

    override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
        val change = userObject as? Change ?: return null
        val root = ReviewGit.repository(myProject, review)?.root ?: return null
        val delegate = ChangeDiffRequestProducer.create(myProject, change) ?: return null
        return ReviewChangeDiffProducer(
            delegate,
            review,
            VcsFileUtil.relativePath(root, ChangesUtil.getFilePath(change))
        )
    }

    /** Runs on a background thread from the async tree model. */
    private fun loadChanges(): Collection<Change> {
        val root = ReviewGit.repository(myProject, review)?.root ?: return emptyList()
        val baseCommit = review.reloadData()?.baseCommit ?: return emptyList()
        val mergeBase = review.gitService.getMergeBase(baseCommit, "HEAD") ?: baseCommit

        return try {
            GitChangeUtils.getDiff(myProject, root, mergeBase, HEAD, null)
        } catch (e: VcsException) {
            thisLogger().warn("Cannot compute review changes against $mergeBase", e)
            emptyList()
        }
    }

    init {
        // AsyncChangesBrowserBase leaves this to subclasses; running it last keeps every field above
        // initialized before init() calls back into getChangesTreeModel().
        init()
    }

    private companion object {
        const val HEAD = "HEAD"
    }
}
