package com.codereview.local.services

import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus

class CommentAnchorResolver {

    /**
     * Re-anchor a comment against the current file lines.
     * Returns a new FileComment with updated line and possibly status.
     */
    fun resolve(comment: FileComment, currentLines: List<String>): FileComment {
        val matchingIndices = currentLines.withIndex()
            .filter { it.value == comment.lineText }
            .map { it.index }

        if (matchingIndices.isEmpty()) {
            return comment.copy(status = FileCommentStatus.OUTDATED)
        }

        if (matchingIndices.size == 1) {
            return comment.copy(line = matchingIndices[0] + 1)
        }

        val disambiguated = matchingIndices.singleOrNull { idx ->
            contextMatches(currentLines, idx, comment.contextBefore, comment.contextAfter)
        }

        return if (disambiguated != null) {
            comment.copy(line = disambiguated + 1)
        } else {
            comment.copy(status = FileCommentStatus.OUTDATED)
        }
    }

    private fun contextMatches(
        lines: List<String>, anchorIdx: Int,
        before: List<String>, after: List<String>
    ): Boolean {
        before.asReversed().forEachIndexed { offset, expected ->
            val idx = anchorIdx - 1 - offset
            if (idx < 0 || lines[idx] != expected) return false
        }
        after.forEachIndexed { offset, expected ->
            val idx = anchorIdx + 1 + offset
            if (idx >= lines.size || lines[idx] != expected) return false
        }
        return true
    }
}
