package com.codereview.local.services

import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus

class CommentAnchorResolver {

    /** Build a map from lineText to the (0-based) indices where it occurs in [currentLines]. */
    fun buildIndex(currentLines: List<String>): Map<String, List<Int>> {
        val index = mutableMapOf<String, MutableList<Int>>()
        currentLines.forEachIndexed { i, line ->
            index.getOrPut(line) { mutableListOf() }.add(i)
        }
        return index
    }

    /** Re-anchor every comment in one pass using a precomputed line index. */
    fun resolveAll(comments: List<FileComment>, currentLines: List<String>): List<FileComment> {
        val index = buildIndex(currentLines)
        return comments.map { resolveWithIndex(it, currentLines, index) }
    }

    /** Re-anchor a single comment against the current file lines. */
    fun resolve(comment: FileComment, currentLines: List<String>): FileComment {
        return resolveWithIndex(comment, currentLines, buildIndex(currentLines))
    }

    private fun resolveWithIndex(
        comment: FileComment,
        currentLines: List<String>,
        index: Map<String, List<Int>>
    ): FileComment {
        val matchingIndices = index[comment.lineText] ?: emptyList()

        if (matchingIndices.isEmpty()) {
            return comment.copy(status = FileCommentStatus.OUTDATED)
        }
        if (matchingIndices.size == 1) {
            return comment.copy(line = matchingIndices[0] + 1)
        }
        // multiple matches: use context
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
