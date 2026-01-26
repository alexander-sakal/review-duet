package com.codereview.local.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

/**
 * Creates DiffRequest objects for review files.
 */
object ReviewDiffRequestProducer {

    fun createDiffRequest(
        project: Project,
        basePath: Path,
        filePath: String,
        baseCommit: String
    ): SimpleDiffRequest? {
        val contentFactory = DiffContentFactory.getInstance()

        // Get git repository
        val repoManager = GitRepositoryManager.getInstance(project)
        val baseVf = LocalFileSystem.getInstance().findFileByNioFile(basePath) ?: return null
        val repo = repoManager.getRepositoryForFile(baseVf) ?: return null

        // Get base content from git
        val fullPath = basePath.resolve(filePath)
        val currentVf = LocalFileSystem.getInstance().findFileByNioFile(fullPath)

        val baseContent = try {
            val contentBytes = git4idea.util.GitFileUtils.getFileContent(
                project, repo.root, baseCommit, filePath
            )
            contentFactory.create(String(contentBytes), currentVf?.fileType)
        } catch (e: Exception) {
            contentFactory.createEmpty()
        }

        // Get current content
        val currentContent = if (currentVf != null && currentVf.exists()) {
            contentFactory.create(project, currentVf)
        } else {
            contentFactory.createEmpty()
        }

        return SimpleDiffRequest(
            "Review: $filePath",
            baseContent,
            currentContent,
            baseCommit,
            "Current"
        )
    }
}
