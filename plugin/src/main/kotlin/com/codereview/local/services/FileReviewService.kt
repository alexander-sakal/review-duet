package com.codereview.local.services

import com.codereview.local.model.FileReview
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path as KPath

@Service(Service.Level.PROJECT)
class FileReviewService private constructor(private val resolveRepoRoot: (Path) -> Path?) {

    constructor(project: Project) : this(projectRepoRootResolver(project))

    companion object {
        private fun projectRepoRootResolver(project: Project): (Path) -> Path? = { target ->
            // Walk up from the target until we find a directory containing .git or hit project base.
            var found: Path? = null
            var dir: Path? = target.parent
            while (dir != null && found == null) {
                if (dir.resolve(".git").toFile().exists()) {
                    found = dir
                } else if (project.basePath != null && dir.toString() == project.basePath) {
                    found = dir
                } else {
                    dir = dir.parent
                }
            }
            found ?: project.basePath?.let { KPath(it) }
        }

        fun getInstance(project: Project): FileReviewService =
            project.getService(FileReviewService::class.java)

        /** For tests — pin the repo root explicitly. */
        fun standalone(repoRoot: Path): FileReviewService =
            FileReviewService { repoRoot }
    }

    private val reviews = mutableMapOf<Path, FileReview>()

    fun openOrCreate(targetFile: Path): FileReview {
        reviews[targetFile]?.let { return it }
        val repoRoot = resolveRepoRoot(targetFile)
            ?: throw IllegalStateException("No repo root for $targetFile")
        val relative = repoRoot.relativize(targetFile).toString().replace('\\', '/')
        val storage = repoRoot.resolve(".review-duet/files").resolve("${encodeRelativePath(relative)}.json")
        val review = FileReview(targetFile, storage)
        reviews[targetFile] = review
        return review
    }

    fun close(targetFile: Path) {
        val review = reviews.remove(targetFile) ?: return
        review.delete()
    }

    /**
     * Returns the storage path for a file review without creating anything.
     * Returns null if the repo root cannot be determined.
     */
    fun resolveStoragePath(targetFile: Path): Path? {
        val repoRoot = resolveRepoRoot(targetFile) ?: return null
        val relative = repoRoot.relativize(targetFile).toString().replace('\\', '/')
        return repoRoot.resolve(".review-duet/files").resolve("${encodeRelativePath(relative)}.json")
    }

    fun encodeRelativePath(path: String): String =
        path.replace("%", "%25").replace("/", "%2F")
}
