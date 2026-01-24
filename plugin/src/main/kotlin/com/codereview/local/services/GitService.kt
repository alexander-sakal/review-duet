package com.codereview.local.services

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GitService(private val projectRoot: Path) {

    fun createTag(tagName: String): Boolean {
        return runGitCommand("tag", tagName) == 0
    }

    fun tagExists(tagName: String): Boolean {
        return runGitCommand("rev-parse", "--verify", "refs/tags/$tagName") == 0
    }

    fun getCurrentCommitSha(): String? {
        val result = runGitCommandWithOutput("rev-parse", "--short", "HEAD")
        return result?.trim()
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
}
