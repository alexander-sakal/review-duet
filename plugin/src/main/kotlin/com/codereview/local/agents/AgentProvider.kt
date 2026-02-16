package com.codereview.local.agents

import com.intellij.execution.configurations.GeneralCommandLine

interface AgentProvider {
    val id: String
    val displayName: String
    fun buildCommand(workingDir: String, reviewFilePath: String, skillContent: String): GeneralCommandLine
    fun parseResult(outputLines: List<String>): String?
    fun shouldCloseStdin(): Boolean
}
