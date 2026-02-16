package com.codereview.local.agents

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

class CodexAgentProvider : AgentProvider {
    override val id = "codex"
    override val displayName = "Codex CLI"

    override fun buildCommand(workingDir: String, reviewFilePath: String): GeneralCommandLine {
        return GeneralCommandLine(
            "codex", "--quiet",
            "Process review comments from $reviewFilePath. " +
                "Use the review-duet CLI to list comments (review-duet list --review=$reviewFilePath --status=open), " +
                "fix them, and mark as done (review-duet fix <id> --commit <sha> --review=$reviewFilePath --message \"...\")."
        )
            .withWorkDirectory(File(workingDir))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    override fun parseResult(outputLines: List<String>): String? {
        val text = outputLines.joinToString("").trim()
        return text.ifEmpty { null }
    }

    override fun shouldCloseStdin(): Boolean = false
}
