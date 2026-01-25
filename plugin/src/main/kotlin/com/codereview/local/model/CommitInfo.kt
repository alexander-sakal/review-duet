package com.codereview.local.model

data class CommitInfo(
    val sha: String,
    val shortSha: String,
    val message: String
) {
    val displayText: String
        get() {
            val truncated = if (message.length > 40) message.take(40) + "..." else message
            return "$truncated ($shortSha)"
        }

    override fun toString(): String = displayText
}
