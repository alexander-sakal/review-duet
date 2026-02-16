package com.codereview.local.agents

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

class CodexAgentProvider : AgentProvider {
    override val id = "codex"
    override val displayName = "Codex CLI"

    override fun buildCommand(workingDir: String, reviewFilePath: String, skillContent: String): GeneralCommandLine {
        return GeneralCommandLine(
            "codex", "--quiet",
            "$skillContent\n\nReview file path: $reviewFilePath"
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
