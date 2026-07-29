package com.codereview.local.ui

import com.codereview.local.model.Review
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Resolves a review to the IDE's own [GitRepository], which the git4idea change APIs require.
 *
 * Matches by root path the same way `GitService.discoverRepos` enumerates repos, so any repo the
 * review panel offers resolves here.
 */
internal object ReviewGit {

    fun repository(project: Project, review: Review): GitRepository? {
        val root = review.gitService.projectRoot.toAbsolutePath().normalize()
        return GitRepositoryManager.getInstance(project).repositories
            .find { it.root.toNioPath().toAbsolutePath().normalize() == root }
    }
}
