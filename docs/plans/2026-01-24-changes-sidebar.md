# Changes Sidebar Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a "Changes" tab to the Review tool window showing files changed between selected review rounds.

**Architecture:** Extend GitService with diff operations, create ChangesPanel with ref dropdowns and file list, wrap existing ReviewPanel content in a tabbed interface. Use IntelliJ's DiffManager for native diff viewing.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (JBTabbedPane, JBList, DiffManager), Git CLI

---

## Task 1: Add ChangeType Enum and ChangedFile Data Class

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/model/ReviewData.kt:56` (append at end)

**Step 1: Add the model classes**

Add at end of `ReviewData.kt`:

```kotlin

enum class ChangeType(val symbol: String) {
    ADDED("A"),
    MODIFIED("M"),
    DELETED("D");

    companion object {
        fun fromGitStatus(status: String): ChangeType = when (status) {
            "A" -> ADDED
            "M" -> MODIFIED
            "D" -> DELETED
            else -> MODIFIED
        }
    }
}

data class ChangedFile(
    val path: String,
    val changeType: ChangeType
)
```

**Step 2: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/ReviewData.kt
git commit -m "feat: add ChangeType and ChangedFile model"
```

---

## Task 2: Add getReviewTags() to GitService

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt:53` (append before closing brace)
- Test: `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt`

**Step 1: Write the failing test**

Add to `GitServiceTest.kt`:

```kotlin
@Test
fun `should get review tags sorted`() {
    // Create commits and tags
    tempDir.resolve("test.txt").toFile().writeText("v1")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "c1").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "tag", "review-r0").directory(tempDir.toFile()).start().waitFor()

    tempDir.resolve("test.txt").toFile().writeText("v2")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "c2").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "tag", "review-r1").directory(tempDir.toFile()).start().waitFor()

    // Also create a non-review tag
    ProcessBuilder("git", "tag", "v1.0.0").directory(tempDir.toFile()).start().waitFor()

    val tags = gitService.getReviewTags()

    assertEquals(listOf("review-r0", "review-r1"), tags)
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should get review tags sorted"`
Expected: FAIL - method doesn't exist

**Step 3: Write implementation**

Add to `GitService.kt` before the final `}`:

```kotlin

    fun getReviewTags(): List<String> {
        val output = runGitCommandWithOutput("tag", "-l", "review-r*") ?: return emptyList()
        return output.trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .sorted()
    }
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should get review tags sorted"`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt
git commit -m "feat: add getReviewTags to GitService"
```

---

## Task 3: Add getChangedFiles() to GitService

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`
- Test: `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt`

**Step 1: Add import to GitService.kt**

Add at top of `GitService.kt`:

```kotlin
import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
```

**Step 2: Write the failing test**

Add to `GitServiceTest.kt`:

```kotlin
@Test
fun `should get changed files between refs`() {
    // Initial commit
    tempDir.resolve("existing.txt").toFile().writeText("original")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "c1").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "tag", "review-r0").directory(tempDir.toFile()).start().waitFor()

    // Make changes
    tempDir.resolve("existing.txt").toFile().writeText("modified")
    tempDir.resolve("new.txt").toFile().writeText("new file")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "c2").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "tag", "review-r1").directory(tempDir.toFile()).start().waitFor()

    val changes = gitService.getChangedFiles("review-r0", "review-r1")

    assertEquals(2, changes.size)
    assertTrue(changes.any { it.path == "existing.txt" && it.changeType == com.codereview.local.model.ChangeType.MODIFIED })
    assertTrue(changes.any { it.path == "new.txt" && it.changeType == com.codereview.local.model.ChangeType.ADDED })
}
```

**Step 3: Run test to verify it fails**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should get changed files between refs"`
Expected: FAIL - method doesn't exist

**Step 4: Write implementation**

Add to `GitService.kt`:

```kotlin

    fun getChangedFiles(fromRef: String, toRef: String): List<ChangedFile> {
        val output = runGitCommandWithOutput("diff", "--name-status", "$fromRef..$toRef") ?: return emptyList()
        return output.trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t")
                ChangedFile(
                    path = parts.getOrElse(1) { "" },
                    changeType = ChangeType.fromGitStatus(parts.getOrElse(0) { "M" })
                )
            }
            .filter { it.path.isNotBlank() }
    }
```

**Step 5: Run test to verify it passes**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should get changed files between refs"`
Expected: PASS

**Step 6: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt
git commit -m "feat: add getChangedFiles to GitService"
```

---

## Task 4: Create ChangesPanel UI Component

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/ChangesPanel.kt`

**Step 1: Create the panel**

Create `ChangesPanel.kt`:

```kotlin
package com.codereview.local.ui

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import com.codereview.local.services.GitService
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.*

class ChangesPanel(
    private val project: Project,
    private val gitService: GitService
) : JPanel(BorderLayout()) {

    private val fromCombo = JComboBox<String>()
    private val toCombo = JComboBox<String>()
    private val listModel = DefaultListModel<ChangedFile>()
    private val fileList = JBList(listModel)

    init {
        border = JBUI.Borders.empty(5)

        // Header with dropdowns
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("From: "))
            add(fromCombo)
            add(Box.createHorizontalStrut(10))
            add(JLabel("To: "))
            add(toCombo)
            add(Box.createHorizontalGlue())
        }

        // Setup combos
        fromCombo.addActionListener { refreshFileList() }
        toCombo.addActionListener { refreshFileList() }

        // Setup file list
        fileList.cellRenderer = ChangedFileCellRenderer()
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                fileList.selectedValue?.let { openDiff(it) }
            }
        }

        add(headerPanel, BorderLayout.NORTH)
        add(JBScrollPane(fileList), BorderLayout.CENTER)

        refreshTags()
    }

    fun refresh() {
        refreshTags()
    }

    private fun refreshTags() {
        val tags = gitService.getReviewTags()
        val selectedFrom = fromCombo.selectedItem as? String
        val selectedTo = toCombo.selectedItem as? String

        fromCombo.removeAllItems()
        toCombo.removeAllItems()

        tags.forEach { tag ->
            fromCombo.addItem(tag)
            toCombo.addItem(tag)
        }

        // Restore selection or set defaults
        if (tags.isNotEmpty()) {
            if (selectedFrom != null && tags.contains(selectedFrom)) {
                fromCombo.selectedItem = selectedFrom
            } else if (tags.size >= 2) {
                fromCombo.selectedIndex = tags.size - 2
            }

            if (selectedTo != null && tags.contains(selectedTo)) {
                toCombo.selectedItem = selectedTo
            } else {
                toCombo.selectedIndex = tags.size - 1
            }
        }

        refreshFileList()
    }

    private fun refreshFileList() {
        val fromRef = fromCombo.selectedItem as? String ?: return
        val toRef = toCombo.selectedItem as? String ?: return

        if (fromRef == toRef) {
            listModel.clear()
            return
        }

        val changes = gitService.getChangedFiles(fromRef, toRef)
        listModel.clear()
        changes.forEach { listModel.addElement(it) }
    }

    private fun openDiff(file: ChangedFile) {
        val fromRef = fromCombo.selectedItem as? String ?: return
        val toRef = toCombo.selectedItem as? String ?: return

        val fromContent = when (file.changeType) {
            ChangeType.ADDED -> ""
            else -> gitService.getFileAtRef(fromRef, file.path) ?: ""
        }

        val toContent = when (file.changeType) {
            ChangeType.DELETED -> ""
            else -> gitService.getFileAtRef(toRef, file.path) ?: ""
        }

        val contentFactory = DiffContentFactory.getInstance()
        val request = SimpleDiffRequest(
            "${file.path} ($fromRef → $toRef)",
            contentFactory.create(fromContent),
            contentFactory.create(toContent),
            fromRef,
            toRef
        )

        DiffManager.getInstance().showDiff(project, request)
    }

    private class ChangedFileCellRenderer : ColoredListCellRenderer<ChangedFile>() {
        override fun customizeCellRenderer(
            list: JList<out ChangedFile>,
            value: ChangedFile,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val (color, attrs) = when (value.changeType) {
                ChangeType.ADDED -> JBColor.GREEN to SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                ChangeType.MODIFIED -> JBColor.BLUE to SimpleTextAttributes.REGULAR_ATTRIBUTES
                ChangeType.DELETED -> JBColor.RED to SimpleTextAttributes.REGULAR_ATTRIBUTES
            }

            append("${value.changeType.symbol}  ", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color))
            append(value.path, attrs)
        }
    }
}
```

**Step 2: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ChangesPanel.kt
git commit -m "feat: add ChangesPanel UI component"
```

---

## Task 5: Add Tabbed Interface to ReviewPanel

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Add import for JBTabbedPane**

At top of `ReviewPanel.kt`, ensure these imports exist (add if missing):

```kotlin
import javax.swing.JTabbedPane
```

**Step 2: Add changesPanel field after gitService**

After line 25 (`private val gitService: GitService by lazy { GitService(basePath) }`), add:

```kotlin
    private val changesPanel: ChangesPanel by lazy { ChangesPanel(project, gitService) }
```

**Step 3: Modify showActiveReviewPanel() to use tabs**

Replace the entire `showActiveReviewPanel()` method (lines 78-112) with:

```kotlin
    private fun showActiveReviewPanel() {
        val data = reviewService.loadReviewData() ?: return

        // Header
        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 0, 10, 0)

            add(JBLabel("Round: ${data.currentRound} (vs ${data.baseRef})"))

            val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
            val totalCount = data.comments.size
            add(JBLabel("Progress: $resolvedCount/$totalCount resolved"))
        }

        // Comment list panel
        val commentsContent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val commentList = CommentListPanel(data.comments) { comment ->
                showCommentDetails(comment)
            }
            add(commentList, BorderLayout.CENTER)
        }

        // Tabbed pane
        val tabbedPane = JTabbedPane().apply {
            addTab("Comments", commentsContent)
            addTab("Changes", changesPanel)
        }

        // Action buttons
        val buttonPanel = JBPanel<JBPanel<*>>().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT)
            add(JButton("New Round").apply {
                addActionListener { startNewRound() }
            })
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
        }

        add(headerPanel, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }
```

**Step 4: Update refresh() to also refresh changesPanel**

In the `refresh()` method (around line 32), after `removeAll()` and before the if statement, or at the end before `revalidate()`, add a call to refresh the changes panel. Replace the refresh method with:

```kotlin
    fun refresh() {
        removeAll()

        if (reviewService.hasActiveReview()) {
            changesPanel.refresh()
            showActiveReviewPanel()
        } else {
            showNoReviewPanel()
        }

        revalidate()
        repaint()
    }
```

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "feat: add tabbed interface with Changes tab"
```

---

## Task 6: Manual Testing

**Step 1: Build and run the plugin**

```bash
cd plugin && ./gradlew runIde
```

**Step 2: Test the feature**

1. Open a project with an existing review (or start a new one)
2. Create a few commits
3. Click "New Round" to create review-r1
4. Switch to the "Changes" tab
5. Verify dropdowns show review-r0 and review-r1
6. Verify file list shows changed files
7. Click a file and verify diff opens

**Step 3: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: address issues from manual testing"
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Add ChangeType/ChangedFile models | ReviewData.kt |
| 2 | Add getReviewTags() | GitService.kt, GitServiceTest.kt |
| 3 | Add getChangedFiles() | GitService.kt, GitServiceTest.kt |
| 4 | Create ChangesPanel | ChangesPanel.kt (new) |
| 5 | Add tabbed interface | ReviewPanel.kt |
| 6 | Manual testing | - |
