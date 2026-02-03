package com.codereview.local.services

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import com.codereview.local.model.CommitInfo
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

class GitService(val projectRoot: Path) {

    companion object {
        /**
         * Find all git repositories using IntelliJ's VCS manager.
         * Returns list of paths to repo roots.
         */
        fun discoverRepos(project: Project): List<Path> {
            val gitRepoManager = GitRepositoryManager.getInstance(project)
            return gitRepoManager.repositories
                .map { it.root.toNioPath() }
                .sortedBy { it.fileName.toString() }
        }

        /**
         * Find the repo root that contains the given file path.
         * Returns null if file is not in any known repo.
         */
        fun findRepoForFile(project: Project, filePath: String): Path? {
            val repos = discoverRepos(project)
            val filePathNormalized = Path.of(filePath).toAbsolutePath().normalize()

            // Find repo whose root is an ancestor of the file
            return repos.find { repo ->
                filePathNormalized.startsWith(repo.toAbsolutePath().normalize())
            }
        }

        /**
         * Get the relative path of a file within its repo.
         * Returns null if file is not in any known repo.
         */
        fun getRelativePath(project: Project, absoluteFilePath: String): Pair<Path, String>? {
            val repoRoot = findRepoForFile(project, absoluteFilePath) ?: return null
            val relativePath = repoRoot.toAbsolutePath().normalize()
                .relativize(Path.of(absoluteFilePath).toAbsolutePath().normalize())
                .toString()
            return repoRoot to relativePath
        }
    }

    fun createTag(tagName: String): Boolean {
        return runGitCommand("tag", tagName) == 0
    }

    fun createTagAtCommit(tagName: String, commitSha: String): Boolean {
        return runGitCommand("tag", tagName, commitSha) == 0
    }

    fun tagExists(tagName: String): Boolean {
        return runGitCommand("rev-parse", "--verify", "refs/tags/$tagName") == 0
    }

    fun getCurrentCommitSha(): String? {
        val result = runGitCommandWithOutput("rev-parse", "--short", "HEAD")
        return result?.trim()
    }

    fun getParentCommitSha(commitSha: String): String? {
        val result = runGitCommandWithOutput("rev-parse", "$commitSha^")
        return result?.trim()
    }

    fun getCurrentBranch(): String? {
        val result = runGitCommandWithOutput("branch", "--show-current")
        return result?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getFileAtRef(ref: String, filePath: String): String? {
        return runGitCommandWithOutput("show", "$ref:$filePath")
    }

    private fun runGitCommand(vararg args: String): Int {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
            process.waitFor(30, TimeUnit.SECONDS)
            process.exitValue()
        } catch (e: Exception) {
            -1
        }
    }

    private fun runGitCommandWithOutput(vararg args: String): String? {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
            process.waitFor(30, TimeUnit.SECONDS)
            if (process.exitValue() == 0) {
                process.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getReviewTags(): List<String> {
        val output = runGitCommandWithOutput("tag", "-l", "review-r*") ?: return emptyList()
        return output.trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .sorted()
    }

    fun getChangedFiles(fromRef: String, toRef: String): List<ChangedFile> {
        val output = runGitCommandWithOutput("diff", "--name-status", "$fromRef..$toRef") ?: return emptyList()
        return output.trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t")
                ChangedFile(
                    path = parts.getOrElse(1) { "" },
                    changeType = ChangeType.fromGitStatus(parts.getOrElse(0) { "M" })
                )
            }
            .filter { it.path.isNotBlank() }
    }

    fun getChangedFilePaths(fromRef: String, toRef: String): Set<String> {
        val output = runGitCommandWithOutput("diff", "--name-only", "$fromRef..$toRef") ?: return emptySet()
        return output.trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun getRecentCommits(limit: Int = 25): List<CommitInfo> {
        val output = runGitCommandWithOutput("log", "--format=%H|%h|%s", "-n", limit.toString())
            ?: return emptyList()
        return output.trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("|", limit = 3)
                CommitInfo(
                    sha = parts.getOrElse(0) { "" },
                    shortSha = parts.getOrElse(1) { "" },
                    message = parts.getOrElse(2) { "" }
                )
            }
            .filter { it.sha.isNotBlank() }
    }

    fun getCommitInfo(commitSha: String): CommitInfo? {
        val output = runGitCommandWithOutput("log", "--format=%H|%h|%s", "-n", "1", commitSha)
            ?: return null
        val line = output.trim()
        if (line.isBlank()) return null
        val parts = line.split("|", limit = 3)
        return CommitInfo(
            sha = parts.getOrElse(0) { "" },
            shortSha = parts.getOrElse(1) { "" },
            message = parts.getOrElse(2) { "" }
        )
    }
}
