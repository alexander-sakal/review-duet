package com.codereview.local.ui

import com.codereview.local.model.Review
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode

/**
 * Wraps the platform's [ChangeDiffRequestProducer] so review data rides along with each request.
 *
 * Delegating means the diff is the IDE's own: when the change's after-revision is the working-tree
 * file the right side is editable and gets revert-change gutter actions.
 *
 * [relativePath] is passed explicitly because the comment gutter needs the repo-relative path, and
 * the platform's request titles are not in a format worth parsing.
 */
internal class ReviewChangeDiffProducer(
    private val delegate: ChangeDiffRequestProducer,
    private val review: Review,
    private val relativePath: String,
    private val commentId: Int? = null
) : ChangeDiffRequestChain.Producer {

    override fun getName(): String = delegate.name
    override fun getFilePath(): FilePath = delegate.filePath
    override fun getFileStatus(): FileStatus = delegate.fileStatus
    override fun getTag(): ChangesBrowserNode.Tag? = delegate.tag

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest =
        delegate.process(context, indicator).apply {
            putUserData(ChangesPanel.REVIEW_KEY, review)
            putUserData(ChangesPanel.FILE_PATH_KEY, relativePath)
            if (commentId != null) {
                putUserData(ChangesPanel.IS_COMMENT_REVIEW_KEY, true)
                putUserData(ChangesPanel.COMMENT_ID_KEY, commentId)
            }
        }
}
