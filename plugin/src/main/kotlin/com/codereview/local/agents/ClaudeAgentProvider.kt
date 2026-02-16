package com.codereview.local.agents

import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

class ClaudeAgentProvider : AgentProvider {
    override val id = "claude"
    override val displayName = "Claude Code"

    override fun buildCommand(workingDir: String, reviewFilePath: String, skillContent: String): GeneralCommandLine {
        return GeneralCommandLine(
            "claude", "-p",
            "/review-duet:process --review=$reviewFilePath",
            "--output-format", "stream-json",
            "--verbose"
        )
            .withWorkDirectory(File(workingDir))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    override fun parseResult(outputLines: List<String>): String? {
        val fullOutput = outputLines.joinToString("")
        for (line in fullOutput.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                val json = JsonParser.parseString(trimmed).asJsonObject
                if (json.get("type")?.asString == "result") {
                    return json.get("result")?.asString
                }
            } catch (_: Exception) {
                // Skip malformed lines
            }
        }
        return null
    }

    override fun shouldCloseStdin(): Boolean = true
}
