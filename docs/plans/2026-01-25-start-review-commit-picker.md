# Start Review with Commit Picker Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace "Start Feature Development" with "Start Review" and add a commit picker dropdown to select any recent commit as the review baseline.

**Architecture:** Add CommitInfo data class to model commits, extend GitService with getRecentCommits() method, refactor ReviewPanel to show a dropdown + button layout instead of single button.

**Tech Stack:** Kotlin, JetBrains Platform SDK (Swing), JUnit 5

---

### Task 1: Add CommitInfo Data Class

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/model/CommitInfo.kt`

**Step 1: Create the CommitInfo data class**

```kotlin
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
```

**Step 2: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/CommitInfo.kt
git commit -m "feat: add CommitInfo data class for commit picker"
```

---

### Task 2: Add getRecentCommits to GitService

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`
- Test: `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt`

**Step 1: Write the failing test**

Add to `GitServiceTest.kt`:

```kotlin
@Test
fun `should get recent commits`() {
    // Create multiple commits
    tempDir.resolve("test.txt").toFile().writeText("v1")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "First commit").directory(tempDir.toFile()).start().waitFor()

    tempDir.resolve("test.txt").toFile().writeText("v2")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "Second commit").directory(tempDir.toFile()).start().waitFor()

    tempDir.resolve("test.txt").toFile().writeText("v3")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "Third commit").directory(tempDir.toFile()).start().waitFor()

    val commits = gitService.getRecentCommits(10)

    assertEquals(3, commits.size)
    assertEquals("Third commit", commits[0].message)
    assertEquals("Second commit", commits[1].message)
    assertEquals("First commit", commits[2].message)
    assertTrue(commits[0].sha.length == 40)
    assertTrue(commits[0].shortSha.length == 7)
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should get recent commits"`
Expected: FAIL - unresolved reference getRecentCommits

**Step 3: Write minimal implementation**

Add to `GitService.kt` (add import at top):

```kotlin
import com.codereview.local.model.CommitInfo
```

Add method:

```kotlin
fun getRecentCommits(limit: Int = 25): List<CommitInfo> {
    val output = runGitCommandWithOutput("log", "--format=%H|%h|%s", "-n", limit.toString())
        ?: return emptyList()
    return output.trim()
        .split("\n")
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split("|", limit = 3)
            CommitInfo(
                sha = parts.getOrElse(0) { "" },
                shortSha = parts.getOrElse(1) { "" },
                message = parts.getOrElse(2) { "" }
            )
        }
}
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should get recent commits"`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt
git commit -m "feat: add getRecentCommits to GitService"
```

---

### Task 3: Add createTagAtCommit to GitService

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`
- Test: `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt`

**Step 1: Write the failing test**

Add to `GitServiceTest.kt`:

```kotlin
@Test
fun `should create tag at specific commit`() {
    // Create two commits
    tempDir.resolve("test.txt").toFile().writeText("v1")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "First commit").directory(tempDir.toFile()).start().waitFor()

    val firstCommitSha = gitService.getCurrentCommitSha()

    tempDir.resolve("test.txt").toFile().writeText("v2")
    ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor()
    ProcessBuilder("git", "commit", "-m", "Second commit").directory(tempDir.toFile()).start().waitFor()

    // Create tag at first commit (not HEAD)
    val result = gitService.createTagAtCommit("review-r0", firstCommitSha!!)

    assertTrue(result)
    assertTrue(gitService.tagExists("review-r0"))

    // Verify tag points to first commit
    val tagSha = runGitCommandWithOutput("rev-parse", "--short", "review-r0")?.trim()
    assertEquals(firstCommitSha, tagSha)
}

private fun runGitCommandWithOutput(vararg args: String): String? {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start()
        process.waitFor()
        if (process.exitValue() == 0) process.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should create tag at specific commit"`
Expected: FAIL - unresolved reference createTagAtCommit

**Step 3: Write minimal implementation**

Add to `GitService.kt`:

```kotlin
fun createTagAtCommit(tagName: String, commitSha: String): Boolean {
    return runGitCommand("tag", tagName, commitSha) == 0
}
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test --tests "GitServiceTest.should create tag at specific commit"`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt
git commit -m "feat: add createTagAtCommit to GitService"
```

---

### Task 4: Refactor ReviewPanel UI

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Add import and ComboBox field**

Add import at top:

```kotlin
import com.codereview.local.model.CommitInfo
import javax.swing.JComboBox
import javax.swing.DefaultComboBoxModel
```

Add field after `changesPanel`:

```kotlin
private var commitComboBox: JComboBox<CommitInfo>? = null
```

**Step 2: Replace showNoReviewPanel method**

Replace the entire `showNoReviewPanel()` method:

```kotlin
private fun showNoReviewPanel() {
    val centerPanel = JBPanel<JBPanel<*>>().apply {
        layout = GridBagLayout()

        val label = JBLabel("No active review").apply {
            horizontalAlignment = SwingConstants.CENTER
        }

        val selectLabel = JBLabel("Select baseline commit:")

        val commits = gitService.getRecentCommits(25)
        commitComboBox = JComboBox(DefaultComboBoxModel(commits.toTypedArray())).apply {
            if (commits.isNotEmpty()) selectedIndex = 0
        }

        val button = JButton("Start Review").apply {
            addActionListener { startReview() }
        }

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = JBUI.insets(5)
            fill = GridBagConstraints.HORIZONTAL
        }
        add(label, gbc)

        gbc.gridy = 1
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        add(selectLabel, gbc)

        gbc.gridy = 2
        gbc.insets = JBUI.insets(5)
        add(commitComboBox, gbc)

        gbc.gridy = 3
        add(button, gbc)
    }

    add(centerPanel, BorderLayout.CENTER)
}
```

**Step 3: Rename and update startFeatureDevelopment to startReview**

Replace the entire `startFeatureDevelopment()` method:

```kotlin
private fun startReview() {
    val selectedCommit = commitComboBox?.selectedItem as? CommitInfo
    if (selectedCommit == null) {
        com.intellij.openapi.ui.Messages.showErrorDialog(
            project,
            "Please select a commit to start the review from.",
            "No Commit Selected"
        )
        return
    }

    // Create baseline tag
    val tagName = "review-r0"
    if (gitService.tagExists(tagName)) {
        val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            "Tag '$tagName' already exists. Delete and recreate?",
            "Tag Exists",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        )
        if (result != com.intellij.openapi.ui.Messages.YES) return
        // Note: For MVP, we'll skip the delete step
    }

    if (!gitService.createTagAtCommit(tagName, selectedCommit.sha)) {
        com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to create git tag", "Git Error")
        return
    }

    // Initialize review
    reviewService.initializeReview(tagName)
    refresh()

    com.intellij.openapi.ui.Messages.showInfoMessage(
        project,
        "Review started!\n\nBaseline tag '$tagName' created at commit ${selectedCommit.shortSha}",
        "Review Initialized"
    )
}
```

**Step 4: Run the plugin to manually verify**

Run: `cd plugin && ./gradlew runIde`

Verify:
- Tool window shows "Select baseline commit:" label
- Dropdown shows recent commits with format "message (sha)"
- "Start Review" button creates tag at selected commit

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "feat: replace Start Feature Development with Start Review + commit picker"
```

---

### Task 5: Run All Tests

**Step 1: Run full test suite**

Run: `cd plugin && ./gradlew test`
Expected: All tests pass

**Step 2: If tests fail, fix and re-run**

---

## Summary

Files changed:
- `plugin/src/main/kotlin/com/codereview/local/model/CommitInfo.kt` (new)
- `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt` (2 new methods)
- `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt` (2 new tests)
- `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt` (UI refactor)
