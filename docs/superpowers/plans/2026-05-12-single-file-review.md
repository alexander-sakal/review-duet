# Single-File Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a parallel single-file review flow — right-click a file in the JetBrains Project view → comment → hand to a one-pass AI agent — without touching the existing branch-review code.

**Architecture:** New `FileReview` domain object stored at `.review-duet/files/<encoded-path>.json`, owned by a new `FileReviewService`, rendered in a new `Review Duet Files` tool window, and processed by a new `review-duet:process-file` skill driven through the existing `AgentProcessService` (parameterized to load the new skill folder).

**Tech Stack:** Kotlin 2.1, IntelliJ Platform 2.11 plugin SDK, Gson 2.13, JUnit Jupiter, Git4Idea. CLI is TypeScript + vitest.

**Spec:** `docs/superpowers/specs/2026-05-12-single-file-review-design.md`

---

## File Map

**Create (plugin):**
- `plugin/src/main/kotlin/com/codereview/local/model/FileReviewData.kt`
- `plugin/src/main/kotlin/com/codereview/local/model/FileReviewDataSerializer.kt`
- `plugin/src/main/kotlin/com/codereview/local/model/FileReview.kt`
- `plugin/src/main/kotlin/com/codereview/local/services/FileReviewService.kt`
- `plugin/src/main/kotlin/com/codereview/local/services/CommentAnchorResolver.kt`
- `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewToolWindowFactory.kt`
- `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt`
- `plugin/src/main/kotlin/com/codereview/local/actions/ReviewFileAction.kt`
- `plugin/src/test/kotlin/com/codereview/local/model/FileReviewDataSerializerTest.kt`
- `plugin/src/test/kotlin/com/codereview/local/services/CommentAnchorResolverTest.kt`
- `plugin/src/test/kotlin/com/codereview/local/services/FileReviewServiceTest.kt`

**Create (skill):**
- `skills/review-duet:process-file/SKILL.md`

**Create (CLI):**
- `cli/src/commands/file-review.test.ts` (or extend existing tests)

**Modify:**
- `plugin/src/main/kotlin/com/codereview/local/services/AgentProcessService.kt` — parameterize `loadSkillContent` by skill folder, add `runFileReview()` entry point.
- `plugin/src/main/resources/META-INF/plugin.xml` — register tool window, action.

---

## Task 1: `FileReviewData` record + serializer

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/model/FileReviewData.kt`
- Create: `plugin/src/main/kotlin/com/codereview/local/model/FileReviewDataSerializer.kt`
- Test: `plugin/src/test/kotlin/com/codereview/local/model/FileReviewDataSerializerTest.kt`

### Step 1: Write the failing test

Create `FileReviewDataSerializerTest.kt`:

```kotlin
package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FileReviewDataSerializerTest {

    private val serializer = FileReviewDataSerializer()

    @Test
    fun `round-trips a populated file review`() {
        val original = FileReviewData(
            version = 1,
            mode = "file",
            filePath = "docs/foo-spec.md",
            fileHashAtStart = "abc123",
            lastSeenHash = "abc123",
            createdAt = "2026-05-12T10:00:00Z",
            comments = mutableListOf(
                FileComment(
                    id = 1,
                    line = 42,
                    lineText = "## Authentication flow",
                    contextBefore = listOf("", "Previous section"),
                    contextAfter = listOf("", "We use OAuth..."),
                    text = "this section needs a diagram",
                    status = FileCommentStatus.OPEN,
                    createdAt = "2026-05-12T10:01:00Z"
                )
            )
        )

        val json = serializer.serialize(original)
        val roundTripped = serializer.deserialize(json)

        assertEquals(original, roundTripped)
    }

    @Test
    fun `serializes status as lowercase string`() {
        val data = FileReviewData(
            version = 1, mode = "file", filePath = "x.md",
            fileHashAtStart = "h", lastSeenHash = "h",
            createdAt = "t", comments = mutableListOf(
                FileComment(1, 1, "x", emptyList(), emptyList(), "t", FileCommentStatus.OUTDATED, "t")
            )
        )
        val json = serializer.serialize(data)
        assertTrue(json.contains("\"status\": \"outdated\""), "got: $json")
    }

    @Test
    fun `deserializes all status values`() {
        for (status in FileCommentStatus.entries) {
            val json = """
            { "version": 1, "mode": "file", "filePath": "x.md",
              "fileHashAtStart": "h", "lastSeenHash": "h", "createdAt": "t",
              "comments": [{
                "id": 1, "line": 1, "lineText": "x",
                "contextBefore": [], "contextAfter": [],
                "text": "t", "status": "${status.jsonValue}", "createdAt": "t"
              }] }
            """.trimIndent()
            assertEquals(status, serializer.deserialize(json).comments[0].status)
        }
    }
}
```

### Step 2: Run test to verify it fails

Run: `cd plugin && ./gradlew test --tests "com.codereview.local.model.FileReviewDataSerializerTest"`
Expected: FAIL (FileReviewData / FileReviewDataSerializer not defined).

### Step 3: Write `FileReviewData.kt`

```kotlin
package com.codereview.local.model

enum class FileCommentStatus(val jsonValue: String) {
    OPEN("open"),
    FIXED("fixed"),
    OUTDATED("outdated");

    companion object {
        fun fromString(value: String): FileCommentStatus =
            entries.find { it.jsonValue == value }
                ?: throw IllegalArgumentException("Unknown file comment status: $value")
    }
}

data class FileComment(
    val id: Int,
    val line: Int,
    val lineText: String,
    val contextBefore: List<String>,
    val contextAfter: List<String>,
    val text: String,
    var status: FileCommentStatus,
    val createdAt: String
)

data class FileReviewData(
    val version: Int,
    val mode: String,
    val filePath: String,
    var fileHashAtStart: String,
    var lastSeenHash: String,
    val createdAt: String,
    val comments: MutableList<FileComment>
) {
    fun getComment(id: Int): FileComment? = comments.find { it.id == id }
    fun getNextCommentId(): Int = (comments.maxOfOrNull { it.id } ?: 0) + 1
}
```

### Step 4: Write `FileReviewDataSerializer.kt`

```kotlin
package com.codereview.local.model

import com.google.gson.*
import java.lang.reflect.Type

class FileReviewDataSerializer {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(FileCommentStatus::class.java, FileCommentStatusAdapter())
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun deserialize(json: String): FileReviewData = gson.fromJson(json, FileReviewData::class.java)

    fun serialize(data: FileReviewData): String = gson.toJson(data)

    private class FileCommentStatusAdapter : JsonSerializer<FileCommentStatus>, JsonDeserializer<FileCommentStatus> {
        override fun serialize(src: FileCommentStatus, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonPrimitive(src.jsonValue)

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FileCommentStatus =
            FileCommentStatus.fromString(json.asString)
    }
}
```

### Step 5: Run test to verify it passes

Run: `cd plugin && ./gradlew test --tests "com.codereview.local.model.FileReviewDataSerializerTest"`
Expected: PASS (3 tests).

### Step 6: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/FileReviewData.kt \
        plugin/src/main/kotlin/com/codereview/local/model/FileReviewDataSerializer.kt \
        plugin/src/test/kotlin/com/codereview/local/model/FileReviewDataSerializerTest.kt
git commit -m "feat: add FileReviewData record and JSON serializer"
```

---

## Task 2: `CommentAnchorResolver`

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/services/CommentAnchorResolver.kt`
- Test: `plugin/src/test/kotlin/com/codereview/local/services/CommentAnchorResolverTest.kt`

### Step 1: Write the failing test

```kotlin
package com.codereview.local.services

import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CommentAnchorResolverTest {

    private val resolver = CommentAnchorResolver()

    private fun comment(line: Int, lineText: String,
                        before: List<String> = emptyList(),
                        after: List<String> = emptyList()) = FileComment(
        id = 1, line = line, lineText = lineText,
        contextBefore = before, contextAfter = after,
        text = "comment", status = FileCommentStatus.OPEN, createdAt = "t"
    )

    @Test
    fun `unchanged file leaves comment at its original line`() {
        val current = listOf("a", "b", "## Auth", "c")
        val c = comment(line = 3, lineText = "## Auth")
        val result = resolver.resolve(c, current)
        assertEquals(3, result.line)
        assertEquals(FileCommentStatus.OPEN, result.status)
    }

    @Test
    fun `lineText found at a different line re-anchors and stays OPEN`() {
        val current = listOf("inserted", "a", "b", "## Auth", "c")
        val c = comment(line = 3, lineText = "## Auth")
        val result = resolver.resolve(c, current)
        assertEquals(4, result.line)
        assertEquals(FileCommentStatus.OPEN, result.status)
    }

    @Test
    fun `multiple matches disambiguated by contextBefore`() {
        val current = listOf("first", "header", "second", "header", "third")
        val c = comment(line = 2, lineText = "header", before = listOf("first"))
        val result = resolver.resolve(c, current)
        assertEquals(2, result.line)
    }

    @Test
    fun `no match marks comment OUTDATED and keeps original line`() {
        val current = listOf("a", "b", "c")
        val c = comment(line = 3, lineText = "## Auth")
        val result = resolver.resolve(c, current)
        assertEquals(3, result.line)
        assertEquals(FileCommentStatus.OUTDATED, result.status)
    }

    @Test
    fun `ambiguous matches with no resolving context mark OUTDATED`() {
        val current = listOf("dup", "dup", "dup")
        val c = comment(line = 2, lineText = "dup")
        val result = resolver.resolve(c, current)
        assertEquals(FileCommentStatus.OUTDATED, result.status)
    }
}
```

### Step 2: Run test to verify it fails

Run: `cd plugin && ./gradlew test --tests "com.codereview.local.services.CommentAnchorResolverTest"`
Expected: FAIL (CommentAnchorResolver not defined).

### Step 3: Implement `CommentAnchorResolver.kt`

```kotlin
package com.codereview.local.services

import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus

class CommentAnchorResolver {

    /**
     * Re-anchor a comment against the current file lines.
     * Returns a new FileComment with updated line and possibly status.
     */
    fun resolve(comment: FileComment, currentLines: List<String>): FileComment {
        val originalIndex = comment.line - 1
        if (originalIndex in currentLines.indices && currentLines[originalIndex] == comment.lineText) {
            return comment
        }

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
```

### Step 4: Run test to verify it passes

Run: `cd plugin && ./gradlew test --tests "com.codereview.local.services.CommentAnchorResolverTest"`
Expected: PASS (5 tests).

### Step 5: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/CommentAnchorResolver.kt \
        plugin/src/test/kotlin/com/codereview/local/services/CommentAnchorResolverTest.kt
git commit -m "feat: add CommentAnchorResolver for stable line tracking"
```

---

## Task 3: `FileReview` model

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/model/FileReview.kt`

`FileReview` wraps `FileReviewData` plus IO and anchoring. It is *not* a service — `FileReviewService` (Task 4) owns instances of it.

### Step 1: Write `FileReview.kt`

```kotlin
package com.codereview.local.model

import com.codereview.local.services.CommentAnchorResolver
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.*

class FileReview(
    val targetFile: Path,
    val storageFile: Path
) {
    private val serializer = FileReviewDataSerializer()
    private val resolver = CommentAnchorResolver()

    fun loadOrInit(): FileReviewData {
        if (storageFile.exists()) {
            return serializer.deserialize(storageFile.readText())
        }
        val hash = computeHash()
        val data = FileReviewData(
            version = 1,
            mode = "file",
            filePath = targetFile.toString(),
            fileHashAtStart = hash,
            lastSeenHash = hash,
            createdAt = Instant.now().toString(),
            comments = mutableListOf()
        )
        save(data)
        return data
    }

    fun save(data: FileReviewData) {
        storageFile.parent?.createDirectories()
        storageFile.writeText(serializer.serialize(data))
    }

    /**
     * Compute current file hash and, if changed, re-anchor every comment.
     * Returns the (possibly updated) data and persists it on changes.
     */
    fun refresh(data: FileReviewData): FileReviewData {
        if (!targetFile.exists()) return data
        val currentHash = computeHash()
        if (currentHash == data.lastSeenHash) return data

        val currentLines = targetFile.readText().lines()
        val updated = data.comments.map { resolver.resolve(it, currentLines) }
        data.comments.clear()
        data.comments.addAll(updated)
        data.lastSeenHash = currentHash
        save(data)
        return data
    }

    fun delete() {
        if (storageFile.exists()) storageFile.deleteExisting()
    }

    private fun computeHash(): String {
        val bytes = if (targetFile.exists()) targetFile.readBytes() else ByteArray(0)
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
```

### Step 2: Commit

No unit tests for this class directly — it's exercised through `FileReviewServiceTest` (Task 4). Commit alongside the service.

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/FileReview.kt
# don't commit yet — combine with Task 4
```

---

## Task 4: `FileReviewService` (project service)

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/services/FileReviewService.kt`
- Test: `plugin/src/test/kotlin/com/codereview/local/services/FileReviewServiceTest.kt`

### Step 1: Write the failing test

```kotlin
package com.codereview.local.services

import com.codereview.local.model.FileCommentStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import kotlin.io.path.*

class FileReviewServiceTest {

    @Test
    fun `openOrCreate creates a fresh review`(@TempDir tmp: Path) {
        val target = tmp.resolve("docs/foo.md").also {
            it.parent.createDirectories()
            it.writeText("hello\nworld")
        }
        val service = FileReviewService.standalone(repoRoot = tmp)

        val review = service.openOrCreate(target)
        val data = review.loadOrInit()

        assertEquals("file", data.mode)
        assertEquals(target.toString(), data.filePath)
        assertTrue(data.fileHashAtStart.isNotEmpty())
        assertEquals(0, data.comments.size)
    }

    @Test
    fun `openOrCreate twice returns equivalent stored review`(@TempDir tmp: Path) {
        val target = tmp.resolve("a.md").apply { writeText("x") }
        val service = FileReviewService.standalone(repoRoot = tmp)

        val first = service.openOrCreate(target).loadOrInit()
        val second = service.openOrCreate(target).loadOrInit()

        assertEquals(first.createdAt, second.createdAt)
    }

    @Test
    fun `storage path encodes path separators`(@TempDir tmp: Path) {
        val target = tmp.resolve("docs/sub/foo.md").also {
            it.parent.createDirectories()
            it.writeText("x")
        }
        val service = FileReviewService.standalone(repoRoot = tmp)
        val review = service.openOrCreate(target)
        review.loadOrInit()

        val expected = tmp.resolve(".review-duet/files/docs%2Fsub%2Ffoo.md.json")
        assertTrue(expected.exists(), "expected $expected to exist")
    }

    @Test
    fun `paths containing percent sign are escaped`(@TempDir tmp: Path) {
        val service = FileReviewService.standalone(repoRoot = tmp)
        val encoded = service.encodeRelativePath("a%b/c.md")
        assertEquals("a%25b%2Fc.md", encoded)
    }
}
```

### Step 2: Run test to verify it fails

Run: `cd plugin && ./gradlew test --tests "com.codereview.local.services.FileReviewServiceTest"`
Expected: FAIL.

### Step 3: Write `FileReviewService.kt`

```kotlin
package com.codereview.local.services

import com.codereview.local.model.FileReview
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path as KPath

@Service(Service.Level.PROJECT)
class FileReviewService private constructor(private val resolveRepoRoot: (Path) -> Path?) {

    constructor(project: Project) : this({ target ->
        // Walk up from the target until we find a directory containing .git or hit project base.
        var dir = target.parent
        while (dir != null) {
            if (dir.resolve(".git").toFile().exists()) return@this dir
            if (project.basePath != null && dir.toString() == project.basePath) return@this dir
            dir = dir.parent
        }
        project.basePath?.let { KPath(it) }
    })

    companion object {
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

    fun encodeRelativePath(path: String): String =
        path.replace("%", "%25").replace("/", "%2F")
}
```

### Step 4: Run test to verify it passes

Run: `cd plugin && ./gradlew test --tests "com.codereview.local.services.FileReviewServiceTest"`
Expected: PASS (4 tests).

### Step 5: Commit (includes Task 3)

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/FileReview.kt \
        plugin/src/main/kotlin/com/codereview/local/services/FileReviewService.kt \
        plugin/src/test/kotlin/com/codereview/local/services/FileReviewServiceTest.kt
git commit -m "feat: add FileReview model and FileReviewService"
```

---

## Task 5: `review-duet:process-file` skill

**Files:**
- Create: `skills/review-duet:process-file/SKILL.md`

### Step 1: Write the skill

```markdown
---
name: review-duet:process-file
description: "Process all open file-review comments in a single pass. Edit the target file in-place and mark each comment as fixed via the CLI."
---

# Process File Review Comments

You have been handed a **file review** — comments on a single file (typically a spec or doc). Your job is to apply all open comments in one pass, edit the file in-place, and mark each comment fixed via the CLI.

## Constraints

- **One file in scope.** The review JSON lists exactly one `filePath`. Do not edit other files.
- **No commits.** Leave the file dirty; the user reviews edits in their IDE.
- **No subagents.** You handle all comments yourself in a single pass.

## review-duet CLI Reference

| Command | Purpose |
|---------|---------|
| `review-duet list --review=<path> --status=open` | List open comments |
| `review-duet fix <id> --review=<path> --message "<explanation>"` | Mark comment as fixed |

The `--commit` flag is **not** used in file mode.

## Procedure

1. Run `review-duet list --review=<path> --status=open` to see all open comments. Each prints with `#<id> <file>:<line>` and the comment text.
2. Read the target file. Note that line numbers in comments may have shifted slightly — the `lineText` field in the JSON anchors the intended location.
3. Apply each comment's suggested change directly in the file.
4. For each addressed comment, run `review-duet fix <id> --review=<path> --message "<one-line explanation of what you changed>"`.
5. Comments marked `outdated` (where the original line could not be re-located) should be **skipped** — flag them in your final summary so the user knows.

## Output

After processing, print a short summary: how many comments fixed, how many skipped as outdated, and the file path edited. The plugin parses this for the completion notification.
```

### Step 2: Commit

```bash
git add skills/review-duet:process-file/SKILL.md
git commit -m "feat: add review-duet:process-file skill"
```

---

## Task 6: Parameterize `AgentProcessService` for the new skill

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/AgentProcessService.kt`

Current `loadSkillContent(workingDir)` is hardcoded to `skills/review-duet:process/SKILL.md`. We add a skill-folder parameter and a sibling entry point `runFileReview(...)`.

### Step 1: Modify `loadSkillContent` signature

Change `AgentProcessService.kt:114-126` from:

```kotlin
private fun loadSkillContent(workingDir: String): String {
    val candidates = listOf(
        java.io.File(workingDir, "skills/review-duet:process/SKILL.md"),
        java.io.File(System.getProperty("user.home"), ".claude/skills/review-duet:process/SKILL.md")
    )
    for (candidate in candidates) {
        if (candidate.exists()) {
            return candidate.readText()
        }
    }
    return ""
}
```

to:

```kotlin
private fun loadSkillContent(workingDir: String, skillFolder: String): String {
    val candidates = listOf(
        java.io.File(workingDir, "skills/$skillFolder/SKILL.md"),
        java.io.File(System.getProperty("user.home"), ".claude/skills/$skillFolder/SKILL.md")
    )
    for (candidate in candidates) {
        if (candidate.exists()) {
            return candidate.readText()
        }
    }
    return ""
}
```

### Step 2: Update `runProcess` to take a `skillFolder`

Change the `runProcess(...)` signature and its `loadSkillContent` call site:

```kotlin
fun runProcess(
    workingDir: String,
    reviewFilePath: String,
    provider: AgentProvider,
    skillFolder: String = "review-duet:process",
    onFinished: (() -> Unit)? = null
) {
    if (isRunning(workingDir)) {
        focusRunTab(workingDir)
        return
    }

    val repoName = java.io.File(workingDir).name
    val tabName = "Review Duet — $repoName"

    val skillContent = loadSkillContent(workingDir, skillFolder)
    // ... rest unchanged
}
```

The default keeps the existing branch-review caller working without edits.

### Step 3: Verify compile

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

### Step 4: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/AgentProcessService.kt
git commit -m "refactor: parameterize AgentProcessService skill folder"
```

---

## Task 7: `FileReviewToolWindowFactory` and `FileReviewPanel`

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewToolWindowFactory.kt`
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt`
- Modify: `plugin/src/main/resources/META-INF/plugin.xml`

This task delivers a minimal functional UI. RangeMarker live tracking (Task 10) and external-file-change refresh (Task 9) are layered in next.

### Step 1: Write `FileReviewToolWindowFactory.kt`

```kotlin
package com.codereview.local.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class FileReviewToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = FileReviewPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

### Step 2: Write `FileReviewPanel.kt`

```kotlin
package com.codereview.local.ui

import com.codereview.local.agents.AgentRegistry
import com.codereview.local.model.FileReview
import com.codereview.local.model.FileReviewData
import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus
import com.codereview.local.services.AgentProcessService
import com.codereview.local.services.FileReviewService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton

class FileReviewPanel(private val project: Project) : JBPanel<FileReviewPanel>(BorderLayout()) {

    private val reviewsListModel = DefaultListModel<Path>()
    private val reviewsList = JBList(reviewsListModel)
    private val commentsListModel = DefaultListModel<FileComment>()
    private val commentsList = JBList(commentsListModel)
    private val headerLabel = JBLabel("No file review selected")
    private var current: Pair<FileReview, FileReviewData>? = null

    init {
        layout = BorderLayout()
        add(buildHeader(), BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        reviewsList.addListSelectionListener {
            val selected = reviewsList.selectedValue ?: return@addListSelectionListener
            showReview(selected)
        }
    }

    private fun buildHeader(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(headerLabel, BorderLayout.CENTER)
    }

    private fun buildBody(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val split = javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(reviewsList),
            JBScrollPane(commentsList)
        )
        split.dividerLocation = 200
        add(split, BorderLayout.CENTER)
    }

    private fun buildFooter(): JBPanel<*> = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
        val process = JButton("Process with agent").also { btn ->
            btn.addActionListener { onProcess() }
        }
        val accept = JButton("Accept & close").also { btn ->
            btn.addActionListener { onAcceptAndClose() }
        }
        add(process)
        add(accept)
    }

    fun openReview(targetFile: Path) {
        if (!reviewsListModel.contains(targetFile)) reviewsListModel.addElement(targetFile)
        reviewsList.setSelectedValue(targetFile, true)
        showReview(targetFile)
    }

    private fun showReview(targetFile: Path) {
        val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
        val data = try {
            review.refresh(review.loadOrInit())
        } catch (e: Exception) {
            renderError("Could not load review for ${targetFile.fileName}: ${e.message}")
            return
        }
        if (!targetFile.toFile().exists()) {
            renderError("File no longer exists: ${targetFile.fileName}. Use Accept & close to discard.")
            current = review to data
            return
        }
        current = review to data
        headerLabel.text = "${targetFile.fileName}  (${data.comments.count { it.status == FileCommentStatus.OPEN }} open)"
        commentsListModel.clear()
        data.comments.forEach { commentsListModel.addElement(it) }
    }

    private fun renderError(message: String) {
        headerLabel.text = message
        commentsListModel.clear()
    }

    private fun onProcess() {
        val (review, data) = current ?: return
        val provider = AgentRegistry.all().firstOrNull() ?: return
        AgentProcessService.getInstance(project).runProcess(
            workingDir = project.basePath ?: return,
            reviewFilePath = review.storageFile.toString(),
            provider = provider,
            skillFolder = "review-duet:process-file",
            onFinished = { showReview(Path.of(data.filePath)) }
        )
    }

    private fun onAcceptAndClose() {
        val (review, data) = current ?: return
        FileReviewService.getInstance(project).close(Path.of(data.filePath))
        val target = Path.of(data.filePath)
        reviewsListModel.removeElement(target)
        commentsListModel.clear()
        headerLabel.text = "No file review selected"
        current = null
    }
}
```

> **Note for the implementer:** the comment-adding UX is intentionally deferred — Task 11 hooks `CommentPopup` into the editor for the file under review. For Task 7, the panel can list existing comments only; we'll exercise it by manually authoring a JSON file with one comment when smoke testing.

### Step 3: Register the tool window in `plugin.xml`

Add inside `<extensions defaultExtensionNs="com.intellij">` in `plugin/src/main/resources/META-INF/plugin.xml`, alongside the existing `<toolWindow id="Review Duet" ...>`:

```xml
<toolWindow id="Review Duet Files"
            anchor="right"
            factoryClass="com.codereview.local.ui.FileReviewToolWindowFactory"
            icon="AllIcons.FileTypes.Text"/>
```

### Step 4: Smoke test

Run: `cd plugin && ./gradlew runIde`
- Open any project.
- Manually create a JSON at `<project>/.review-duet/files/test.md.json` with the round-trip shape from Task 1 (1 open comment).
- Expected: `Review Duet Files` tool window appears on right. (Empty until Task 8 adds the action.)

### Step 5: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/FileReviewToolWindowFactory.kt \
        plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt \
        plugin/src/main/resources/META-INF/plugin.xml
git commit -m "feat: add Review Duet Files tool window and panel"
```

---

## Task 8: `ReviewFileAction` and Project view menu integration

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/actions/ReviewFileAction.kt`
- Modify: `plugin/src/main/resources/META-INF/plugin.xml`

### Step 1: Write `ReviewFileAction.kt`

```kotlin
package com.codereview.local.actions

import com.codereview.local.services.FileReviewService
import com.codereview.local.ui.FileReviewPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import java.nio.file.Path

class ReviewFileAction : AnAction() {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 1L * 1024 * 1024 // 1 MB
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isEligible(e.getData(CommonDataKeys.VIRTUAL_FILE))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vfile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!isEligible(vfile)) return

        val target = Path.of(vfile.path)
        FileReviewService.getInstance(project).openOrCreate(target)

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Review Duet Files") ?: return
        toolWindow.activate {
            val content: Content? = toolWindow.contentManager.contents.firstOrNull()
            (content?.component as? FileReviewPanel)?.openReview(target)
        }
    }

    private fun isEligible(vfile: VirtualFile?): Boolean {
        if (vfile == null || vfile.isDirectory) return false
        if (FileTypeRegistry.getInstance().getFileTypeByFile(vfile).isBinary) return false
        if (vfile.length > MAX_FILE_SIZE_BYTES) return false
        return true
    }
}
```

### Step 2: Register the action in `plugin.xml`

Add inside the existing `<actions>` block:

```xml
<action id="CodeReview.ReviewFile"
        class="com.codereview.local.actions.ReviewFileAction"
        text="Review File with Review Duet"
        description="Start a single-file review with Review Duet">
    <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
</action>
```

### Step 3: Smoke test

Run: `cd plugin && ./gradlew runIde`
- Right-click `README.md` or any markdown file in Project view.
- Expected: menu item `Review File with Review Duet` appears.
- Click it: `Review Duet Files` tool window opens with the file listed.
- Right-click on a directory: menu item should be hidden.
- Right-click on a binary file (e.g. an image): menu item should be hidden.

### Step 4: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/actions/ReviewFileAction.kt \
        plugin/src/main/resources/META-INF/plugin.xml
git commit -m "feat: add Review File with Review Duet action"
```

---

## Task 9: External-file-change refresh wiring

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt`

When the user (or the agent) edits the target file, the panel must refresh so the anchor resolver re-runs and the comment list reflects new line numbers / outdated statuses.

### Step 1: Subscribe to `VirtualFileListener` in the panel

Add an init block extension and a method in `FileReviewPanel.kt`:

```kotlin
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection

private var busConnection: MessageBusConnection? = null

init {
    // ... existing init body ...

    busConnection = project.messageBus.connect().also { conn ->
        conn.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val cur = current ?: return
                val watchedPath = Path.of(cur.second.filePath).toAbsolutePath().toString()
                if (events.any { it.path == watchedPath }) {
                    ApplicationManager.getApplication().invokeLater {
                        showReview(Path.of(cur.second.filePath))
                    }
                }
            }
        })
    }
}
```

### Step 2: Smoke test

Run: `cd plugin && ./gradlew runIde`
- Start a file review on a markdown file.
- Add a hand-crafted comment to the JSON pointing at line 5 with `lineText` of that line.
- Edit the file in the IDE, inserting a line above line 5 so the content shifts down.
- Expected: panel refresh shows the comment's line updated; status remains `open`.

### Step 3: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt
git commit -m "feat: refresh file review when the target file changes"
```

---

## Task 10: RangeMarker live tracking (Layer C)

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt`

While the target file is open in an editor, attach `RangeMarker`s so comments follow the user's edits in real time. On document save, persist updated line numbers to the JSON.

### Step 1: Track markers per comment

Add to `FileReviewPanel`:

```kotlin
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager

private val markersByCommentId = mutableMapOf<Int, RangeMarker>()
private var trackedDocument: Document? = null
private var documentListener: DocumentListener? = null

private fun attachMarkers(data: FileReviewData) {
    detachMarkers()
    val vfile = LocalFileSystem.getInstance().findFileByPath(data.filePath) ?: return
    val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return
    trackedDocument = doc

    data.comments.forEach { c ->
        val lineIdx = (c.line - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
        val start = doc.getLineStartOffset(lineIdx)
        val end = doc.getLineEndOffset(lineIdx)
        markersByCommentId[c.id] = doc.createRangeMarker(start, end)
    }

    val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            persistMarkers(data, doc)
        }
    }
    doc.addDocumentListener(listener)
    documentListener = listener
}

private fun detachMarkers() {
    val doc = trackedDocument
    val listener = documentListener
    if (doc != null && listener != null) doc.removeDocumentListener(listener)
    markersByCommentId.values.forEach { it.dispose() }
    markersByCommentId.clear()
    trackedDocument = null
    documentListener = null
}

private fun persistMarkers(data: FileReviewData, doc: Document) {
    var changed = false
    data.comments.forEachIndexed { idx, c ->
        val m = markersByCommentId[c.id] ?: return@forEachIndexed
        if (!m.isValid) return@forEachIndexed
        val newLine = doc.getLineNumber(m.startOffset) + 1
        if (newLine != c.line) {
            data.comments[idx] = c.copy(line = newLine)
            changed = true
        }
    }
    if (changed) {
        current?.first?.save(data)
        ApplicationManager.getApplication().invokeLater {
            commentsListModel.clear()
            data.comments.forEach { commentsListModel.addElement(it) }
        }
    }
}
```

Call `attachMarkers(data)` at the end of `showReview(...)`, and `detachMarkers()` at the start of `showReview(...)` and inside `onAcceptAndClose()`.

### Step 2: Smoke test

Run: `cd plugin && ./gradlew runIde`
- Open a file review with one comment at line 10.
- In the editor, press Enter at the top of the file to insert a blank line.
- Expected: comment row updates to line 11 within ~1 second.

### Step 3: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt
git commit -m "feat: live-track file review comments via RangeMarkers"
```

---

## Task 11: Hook `CommentPopup` for file reviews

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/CommentPopup.kt` (inspect first to confirm)
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt`

The existing branch-review flow lets the user right-click in the editor gutter to add a comment. For file reviews we want the same gutter affordance, but writing to the `FileReview` instead of the branch `Review`.

### Step 1: Read existing `CommentPopup.kt`

```bash
cat plugin/src/main/kotlin/com/codereview/local/ui/CommentPopup.kt
```

Identify the entry point used by the branch flow (typically a gutter action or popup constructor) and the call that appends a comment to `ReviewData`. Mirror it for `FileReviewData`.

### Step 2: Add `addComment(line, text)` to `FileReviewPanel`

```kotlin
fun addComment(targetFile: Path, line: Int, text: String) {
    val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
    val data = review.loadOrInit()
    val vfile = LocalFileSystem.getInstance().findFileByPath(targetFile.toString())
    val doc = vfile?.let { FileDocumentManager.getInstance().getDocument(it) }
    val lineText = doc?.let {
        val idx = (line - 1).coerceIn(0, (it.lineCount - 1).coerceAtLeast(0))
        it.getText(com.intellij.openapi.util.TextRange(it.getLineStartOffset(idx), it.getLineEndOffset(idx)))
    } ?: ""
    val contextBefore = doc?.let { sliceLines(it, line - 2, line - 1) } ?: emptyList()
    val contextAfter = doc?.let { sliceLines(it, line, line + 1) } ?: emptyList()

    data.comments.add(
        FileComment(
            id = data.getNextCommentId(),
            line = line,
            lineText = lineText,
            contextBefore = contextBefore,
            contextAfter = contextAfter,
            text = text,
            status = FileCommentStatus.OPEN,
            createdAt = java.time.Instant.now().toString()
        )
    )
    review.save(data)
    openReview(targetFile)
}

private fun sliceLines(doc: Document, fromLine: Int, toLine: Int): List<String> {
    val result = mutableListOf<String>()
    for (i in fromLine..toLine) {
        if (i in 0 until doc.lineCount) {
            result.add(doc.getText(com.intellij.openapi.util.TextRange(doc.getLineStartOffset(i), doc.getLineEndOffset(i))))
        }
    }
    return result
}
```

### Step 3: Wire the gutter affordance

Adjust the gutter / popup entry point identified in Step 1 so that, when the file under the cursor has an active `FileReview`, the popup writes to `FileReviewPanel.addComment(...)` instead of the branch path. The simplest discriminator: ask `FileReviewService.getInstance(project)` whether `openOrCreate` for that path returns an existing review whose storage file already exists (= active file review). If yes → file mode; otherwise → existing branch behavior.

### Step 4: Smoke test

Run: `cd plugin && ./gradlew runIde`
- Right-click a markdown file → `Review File with Review Duet`.
- Open the file in the editor, right-click in the gutter at line 10, choose "Add comment", type text.
- Expected: comment appears in the `Review Duet Files` panel anchored at line 10.

### Step 5: Commit

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/CommentPopup.kt \
        plugin/src/main/kotlin/com/codereview/local/ui/FileReviewPanel.kt
git commit -m "feat: author file-review comments via the existing gutter popup"
```

---

## Task 12: CLI tolerance for file-mode JSON

**Files:**
- Create or modify: `cli/src/commands/file-review.test.ts`
- Modify (if needed): `cli/src/store.ts`, `cli/src/commands/list.ts`, `cli/src/commands/fix.ts`

### Step 1: Inspect current CLI

```bash
ls cli/src/commands
cat cli/src/types.ts
```

Identify whether the CLI's `ReviewData` type insists on `baseCommit` or whether it tolerates a `mode: "file"` JSON.

### Step 2: Write tests for file-mode

Create `cli/src/commands/file-review.test.ts`:

```typescript
import { describe, it, expect, beforeEach } from "vitest";
import { mkdtempSync, writeFileSync, readFileSync } from "fs";
import { tmpdir } from "os";
import { join } from "path";
import { listCommand } from "./list";
import { fixCommand } from "./fix";

const SAMPLE = {
  version: 1,
  mode: "file",
  filePath: "docs/foo.md",
  fileHashAtStart: "abc",
  lastSeenHash: "abc",
  createdAt: "2026-05-12T10:00:00Z",
  comments: [{
    id: 1, line: 10, lineText: "header",
    contextBefore: [], contextAfter: [],
    text: "needs detail", status: "open",
    createdAt: "2026-05-12T10:01:00Z",
  }],
};

describe("file-mode review JSON", () => {
  let path: string;
  beforeEach(() => {
    const dir = mkdtempSync(join(tmpdir(), "rd-"));
    path = join(dir, "file-review.json");
    writeFileSync(path, JSON.stringify(SAMPLE));
  });

  it("list shows open comments", async () => {
    const out = await listCommand({ review: path, status: "open" });
    expect(out).toContain("#1");
    expect(out).toContain("docs/foo.md:10");
  });

  it("fix marks the comment fixed without requiring --commit", async () => {
    await fixCommand({ review: path, id: 1, message: "added a diagram" });
    const updated = JSON.parse(readFileSync(path, "utf8"));
    expect(updated.comments[0].status).toBe("fixed");
  });
});
```

### Step 3: Run tests to verify they pass or fail

Run: `cd cli && npm test -- file-review`
Expected: either PASS (CLI already tolerant) or FAIL telling you which call requires `baseCommit` / `commit`. Whatever fails, narrow the offending function to accept a file-mode JSON: `baseCommit` becomes optional, the `--commit` flag becomes optional for `fix` when `mode === "file"`.

### Step 4: Loop until tests pass

For each failure, make the minimum change to `store.ts` or the relevant command, then re-run `npm test -- file-review`.

### Step 5: Run the full CLI suite to confirm no regressions

Run: `cd cli && npm test`
Expected: all tests green, including pre-existing.

### Step 6: Commit

```bash
git add cli/src/commands/file-review.test.ts cli/src/
git commit -m "feat(cli): tolerate file-mode review JSON in list and fix"
```

---

## Final smoke test

Run end-to-end against a real agent.

1. `cd plugin && ./gradlew runIde`
2. Open the plugin project itself.
3. Right-click `README.md` in the Project view → `Review File with Review Duet`.
4. In the editor, right-click line 1 gutter → add comment "rephrase the headline".
5. In the panel, click `Process with agent`.
6. Expected: the agent runs (you see output in the `Run` tool window), edits `README.md`, marks comment `fixed`, and the panel updates.
7. Click `Accept & close`. Tool window entry disappears; `.review-duet/files/README.md.json` is gone.

If any of these fail, debug before declaring complete. Do not skip the end-to-end run.

---

## Self-Review Notes

- **Spec coverage:** Action (Task 8), tool window (Task 7), service (Task 4), anchor resolver (Task 2), data model (Task 1, 3), skill (Task 5), agent dispatch (Task 6), external refresh (Task 9), RangeMarker (Task 10), comment authoring (Task 11), CLI tolerance (Task 12). All spec sections covered.
- **Naming:** classes, files, action id, tool window id, and storage paths match the spec's naming table.
- **TDD discipline:** Tasks 1, 2, 4, 12 are strictly TDD. Tasks 3, 5–11 are implementation/UI/wiring where the unit-test surface is thin; they rely on the unit-tested core (anchor resolver, service, serializer) and on manual smoke tests at each step.
- **Frequent commits:** each task commits independently. The plan-merge order is bottom-up, so any task can be reviewed and reverted in isolation.
