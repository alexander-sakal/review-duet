package com.codereview.local.diff

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile

/**
 * Virtual file representing a review diff for a specific file.
 * When opened, creates a diff viewer showing changes since baseCommit.
 */
class ReviewDiffVirtualFile(
    private val project: Project,
    val filePath: String,
    val baseCommit: String
) : LightVirtualFile("Review: $filePath", "") {

    override fun isWritable(): Boolean = false

    override fun isValid(): Boolean = true

    override fun getPath(): String = "review://${project.locationHash}/$filePath"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewDiffVirtualFile) return false
        return project == other.project && filePath == other.filePath && baseCommit == other.baseCommit
    }

    override fun hashCode(): Int {
        var result = project.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + baseCommit.hashCode()
        return result
    }
}
