# Storage Migration & UI Enhancements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rename storage from `.review/` to `.review-duet/{branch}.json`, add multi-repo dropdown, branch display, commit selector with refresh button to plugin UI, and update CLI to accept explicit review path.

**Architecture:** Storage moves to branch-based files at repo root. Plugin UI gets a start review form with repo selector (when multiple repos), branch display, commit dropdown, and refresh button. VCS branch change listener auto-refreshes panel when user switches branches. CLI accepts `--review` flag; skill handles file discovery.

**Tech Stack:** Kotlin (IntelliJ plugin), TypeScript (CLI), Swing UI components

---

## Task 1: Update ReviewService Storage Path

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt:8-17`

**Step 1: Update storage path logic**

Change from `.review/comments.json` to `.review-duet/{branch}.json`:

```kotlin
class ReviewService(private val projectRoot: Path) {

    private val gitService = GitService(projectRoot)
    private val reviewDuetDir: Path = projectRoot.resolve(".review-duet")
    private val serializer = ReviewDataSerializer()

    private var cachedData: ReviewData? = null
    private var currentBranch: String? = null

    private fun getCommentsFile(): Path {
        val branch = currentBranch ?: gitService.getCurrentBranch() ?: "main"
        return reviewDuetDir.resolve("$branch.json")
    }

    fun hasActiveReview(): Boolean = getCommentsFile().exists()
```

**Step 2: Update all references to `commentsFile`**

Replace direct `commentsFile` references with `getCommentsFile()` calls throughout the class:
- `loadReviewData()` - use `getCommentsFile().readText()`
- `saveReviewData()` - use `getCommentsFile().writeText()`
- Check `reviewDuetDir` instead of `reviewDir`

**Step 3: Run plugin to verify compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt
git commit -m "refactor: change storage to .review-duet/{branch}.json"
```

---

## Task 2: Add getCurrentBranch to GitService

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`

**Step 1: Add getCurrentBranch method**

Add after `getParentCommitSha`:

```kotlin
fun getCurrentBranch(): String? {
    val result = runGitCommandWithOutput("branch", "--show-current")
    return result?.trim()?.takeIf { it.isNotBlank() }
}
```

**Step 2: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt
git commit -m "feat: add getCurrentBranch to GitService"
```

---

## Task 3: Add Git Repo Discovery

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`

**Step 1: Add companion object with repo discovery**

Add at the top of GitService class:

```kotlin
companion object {
    /**
     * Find all git repositories within a project directory.
     * Returns list of paths to repo roots (directories containing .git).
     */
    fun discoverRepos(projectRoot: Path): List<Path> {
        val repos = mutableListOf<Path>()

        // Check if project root itself is a repo
        if (projectRoot.resolve(".git").exists()) {
            repos.add(projectRoot)
        }

        // Search for nested repos (one level deep for performance)
        projectRoot.toFile().listFiles()?.forEach { file ->
            if (file.isDirectory && !file.name.startsWith(".")) {
                val nestedGit = file.toPath().resolve(".git")
                if (nestedGit.exists()) {
                    repos.add(file.toPath())
                }
            }
        }

        return repos.sortedBy { it.fileName.toString() }
    }
}
```

**Step 2: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt
git commit -m "feat: add git repo discovery for multi-repo projects"
```

---

## Task 4: Update ReviewPanel endReview for New Path

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt:211-226`

**Step 1: Update endReview dialog text and deletion logic**

```kotlin
private fun endReview() {
    val branch = gitService.getCurrentBranch() ?: "unknown"
    val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
        project,
        "End this review session? The review file for branch '$branch' will be deleted.",
        "End Review",
        com.intellij.openapi.ui.Messages.getQuestionIcon()
    )
    if (result != com.intellij.openapi.ui.Messages.YES) return

    // Delete review file for current branch
    val reviewFile = basePath.resolve(".review-duet").resolve("$branch.json").toFile()
    if (reviewFile.exists()) {
        reviewFile.delete()
    }
    // Clean up empty directory
    val reviewDir = basePath.resolve(".review-duet").toFile()
    if (reviewDir.exists() && reviewDir.listFiles()?.isEmpty() == true) {
        reviewDir.delete()
    }
    refresh()
}
```

**Step 2: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "refactor: update endReview for .review-duet storage"
```

---

## Task 5: Update ReviewFileWatcher for New Path

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/listeners/ReviewFileWatcher.kt:14`

**Step 1: Update path pattern**

```kotlin
event.path?.contains(".review-duet/") == true && event.path?.endsWith(".json") == true
```

**Step 2: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/listeners/ReviewFileWatcher.kt
git commit -m "refactor: update file watcher for .review-duet path"
```

---

## Task 6: Add .review-duet to .gitignore on Review Start

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt`

**Step 1: Add ensureGitignore method**

```kotlin
private fun ensureGitignore() {
    val gitignore = projectRoot.resolve(".gitignore")
    val entry = ".review-duet/"

    if (gitignore.exists()) {
        val content = gitignore.readText()
        if (!content.contains(entry)) {
            val newContent = if (content.endsWith("\n")) {
                "$content$entry\n"
            } else {
                "$content\n$entry\n"
            }
            gitignore.writeText(newContent)
        }
    } else {
        gitignore.writeText("$entry\n")
    }
}
```

**Step 2: Call ensureGitignore in saveReviewData**

In `saveReviewData`, after creating directory:

```kotlin
fun saveReviewData(data: ReviewData) {
    if (!reviewDuetDir.exists()) {
        reviewDuetDir.createDirectories()
        ensureGitignore()
    }
    getCommentsFile().writeText(serializer.serialize(data))
    cachedData = data
}
```

**Step 3: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt
git commit -m "feat: auto-add .review-duet to gitignore"
```

---

## Task 7: Add Repo Selector and Branch Display to Start Panel

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Add class-level fields for repo management**

Add after existing fields:

```kotlin
private var availableRepos: List<Path> = emptyList()
private var selectedRepoPath: Path = basePath
private var repoComboBox: JComboBox<String>? = null

private var reviewService: ReviewService = ReviewService(basePath)
private var gitService: GitService = GitService(basePath)
```

Remove the existing lazy `reviewService` and `gitService` declarations.

**Step 2: Add init block to discover repos**

Update init:

```kotlin
init {
    border = JBUI.Borders.empty(10, 0)
    availableRepos = GitService.discoverRepos(basePath)
    if (availableRepos.isNotEmpty()) {
        selectedRepoPath = availableRepos.first()
        reviewService = ReviewService(selectedRepoPath)
        gitService = GitService(selectedRepoPath)
    }
    refresh()
}
```

**Step 3: Add onRepoSelected method**

```kotlin
private fun onRepoSelected(repoPath: Path) {
    selectedRepoPath = repoPath
    reviewService = ReviewService(repoPath)
    gitService = GitService(repoPath)
    refresh()
}
```

**Step 4: Update showNoReviewPanel with repo selector**

```kotlin
private fun showNoReviewPanel() {
    val centerPanel = JBPanel<JBPanel<*>>().apply {
        layout = GridBagLayout()

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = JBUI.insets(5)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Repo selector (only if multiple repos)
        if (availableRepos.size > 1) {
            val repoLabel = JBLabel("Repository:")
            add(repoLabel, gbc)

            gbc.gridy = 1
            val repoNames = availableRepos.map { it.fileName.toString() }.toTypedArray()
            repoComboBox = JComboBox(DefaultComboBoxModel(repoNames)).apply {
                selectedItem = selectedRepoPath.fileName.toString()
                addActionListener {
                    val selectedName = selectedItem as? String ?: return@addActionListener
                    val repo = availableRepos.find { it.fileName.toString() == selectedName }
                    if (repo != null && repo != selectedRepoPath) {
                        onRepoSelected(repo)
                    }
                }
            }
            add(repoComboBox, gbc)
            gbc.gridy = 2
            gbc.insets = JBUI.insets(10, 5, 5, 5)
        }

        // Branch display
        val currentBranch = gitService.getCurrentBranch() ?: "unknown"
        val branchLabel = JBLabel("Branch: $currentBranch").apply {
            horizontalAlignment = SwingConstants.CENTER
        }
        add(branchLabel, gbc)

        // Commit selector label
        gbc.gridy++
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        val selectLabel = JBLabel("Review changes starting from:")
        add(selectLabel, gbc)

        // Commit dropdown
        gbc.gridy++
        gbc.insets = JBUI.insets(5)
        val commits = gitService.getRecentCommits(25)
        commitComboBox = JComboBox(DefaultComboBoxModel(commits.toTypedArray())).apply {
            if (commits.isNotEmpty()) selectedIndex = 0
        }
        add(commitComboBox, gbc)

        // Start button
        gbc.gridy++
        val button = JButton("Start Review").apply {
            addActionListener { startReview() }
        }
        add(button, gbc)
    }

    add(centerPanel, BorderLayout.CENTER)
}
```

**Step 5: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "ui: add repo selector and branch display to start panel"
```

---

## Task 8: Add Refresh Button Next to Commit Dropdown

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Add imports**

```kotlin
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBPanel
import javax.swing.JPanel
import java.awt.FlowLayout
```

**Step 2: Update commit selector section**

Replace the commit dropdown section with a panel containing dropdown + refresh button:

```kotlin
val commits = gitService.getRecentCommits(25)
commitComboBox = JComboBox(DefaultComboBoxModel(commits.toTypedArray())).apply {
    if (commits.isNotEmpty()) selectedIndex = 0
}

val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
    toolTipText = "Refresh commits"
    addActionListener { refreshCommits() }
}

val commitPanel = JPanel(FlowLayout(FlowLayout.CENTER, 5, 0)).apply {
    isOpaque = false
    add(commitComboBox)
    add(refreshButton)
}
```

Then use `commitPanel` instead of `commitComboBox` in the GridBagConstraints:

```kotlin
gbc.gridy = 3
gbc.insets = JBUI.insets(5)
add(commitPanel, gbc)
```

**Step 3: Update refreshCommits to refresh entire panel**

The refresh button should refresh commits AND check if branch changed (reload review if needed):

```kotlin
private fun refreshCommits() {
    // Full refresh reloads branch, review data, and commits
    refresh()
}
```

This calls the existing `refresh()` method which rebuilds the entire panel with fresh data from `gitService.getCurrentBranch()` and `reviewService.loadReviewData()`.

**Step 4: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "ui: add refresh button for commits dropdown"
```

---

## Task 9: Add VCS Branch Change Listener

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/listeners/ReviewBranchChangeListener.kt`
- Modify: `plugin/src/main/resources/META-INF/plugin.xml`

**Step 1: Create ReviewBranchChangeListener**

```kotlin
package com.codereview.local.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.openapi.wm.ToolWindowManager
import com.codereview.local.ui.ReviewPanel

class ReviewBranchChangeListener(private val project: Project) : BranchChangeListener {

    override fun branchWillChange(branchName: String) {
        // No action needed before change
    }

    override fun branchHasChanged(branchName: String) {
        // Refresh the review panel when branch changes
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Review")
        toolWindow?.contentManager?.contents?.forEach { content ->
            (content.component as? ReviewPanel)?.refresh()
        }
    }
}
```

**Step 2: Register listener in plugin.xml**

Add inside `<projectListeners>` (after the existing ReviewFileWatcher):

```xml
<listener class="com.codereview.local.listeners.ReviewBranchChangeListener"
          topic="com.intellij.openapi.vcs.BranchChangeListener"/>
```

**Step 3: Run compilation**

Run: `cd plugin && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/listeners/ReviewBranchChangeListener.kt
git add plugin/src/main/resources/META-INF/plugin.xml
git commit -m "feat: auto-refresh review panel on branch change"
```

---

## Task 10: Update CLI Store to Accept Explicit Path

**Files:**
- Modify: `cli/src/store.ts`

**Step 1: Update constructor**

```typescript
export class ReviewStore {
  private readonly reviewPath: string;
  private data: ReviewData | null = null;

  constructor(reviewPath: string) {
    this.reviewPath = reviewPath;
  }

  // ... rest unchanged
}
```

**Step 2: Run tests**

Run: `cd cli && npm test`
Expected: Tests may fail (need to update test fixtures)

**Step 3: Commit**

```bash
git add cli/src/store.ts
git commit -m "refactor: CLI store takes explicit review path"
```

---

## Task 11: Update CLI Index to Handle --review Flag

**Files:**
- Modify: `cli/src/index.ts`

**Step 1: Add getReviewPath helper**

```typescript
function getReviewPath(options: Record<string, string>, cwd: string): string {
  if (options.review) {
    return options.review;
  }
  // Default: .review-duet/{branch}.json
  const { execSync } = require('child_process');
  const branch = execSync('git branch --show-current', { cwd, encoding: 'utf-8' }).trim();
  return `${cwd}/.review-duet/${branch}.json`;
}
```

**Step 2: Update runCli to use getReviewPath**

```typescript
export function runCli(argv: string[], cwd: string = process.cwd()): string {
  const { command, args, options } = parseArgs(argv);
  const reviewPath = getReviewPath(options, cwd);
  const store = new ReviewStore(reviewPath);

  // ... rest unchanged
}
```

**Step 3: Update help text**

```typescript
case 'help':
default:
  return `review-duet CLI - Code review helper for Claude Code

Usage:
  review-duet list [--status=<status>] [--review=<path>]   List comments
  review-duet fix <id> --commit <sha> [--message]          Mark as fixed
  review-duet show <id>                                    Show comment details
  review-duet accept                                       Accept changes, move baseline to HEAD

Options:
  --review=<path>  Explicit path to review file (default: .review-duet/{branch}.json)

Statuses: open, fixed, resolved`;
```

**Step 4: Run CLI manually**

Run: `cd cli && npm run build && node dist/index.js help`
Expected: Shows updated help text

**Step 5: Commit**

```bash
git add cli/src/index.ts
git commit -m "feat: CLI accepts --review flag, defaults to branch-based path"
```

---

## Task 12: Update CLI Tests for New Path Logic

**Files:**
- Modify: `cli/src/store.test.ts`
- Modify: `cli/src/cli.test.ts`
- Modify: `cli/src/commands/*.test.ts`

**Step 1: Update store tests**

Tests should now pass explicit paths:

```typescript
describe('ReviewStore', () => {
  it('loads review data from explicit path', () => {
    const store = new ReviewStore('/tmp/test/.review-duet/main.json');
    // ... test logic
  });
});
```

**Step 2: Run tests**

Run: `cd cli && npm test`
Expected: All tests pass

**Step 3: Commit**

```bash
git add cli/src/*.test.ts cli/src/commands/*.test.ts
git commit -m "test: update CLI tests for explicit path constructor"
```

---

## Task 13: Full Integration Test

**Step 1: Build plugin**

Run: `cd plugin && ./gradlew buildPlugin`
Expected: BUILD SUCCESSFUL

**Step 2: Run plugin tests**

Run: `cd plugin && ./gradlew test`
Expected: Tests pass (may need updates for new paths)

**Step 3: Build CLI**

Run: `cd cli && npm run build`
Expected: Build successful

**Step 4: Run CLI tests**

Run: `cd cli && npm test`
Expected: All tests pass

**Step 5: Final commit**

```bash
git add -A
git commit -m "chore: complete storage migration and UI enhancements"
```

---

## Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| Storage | 1-2, 4-6 | Migrate to `.review-duet/{branch}.json`, auto-gitignore |
| Repo Discovery | 3 | Find all git repos in project |
| UI | 7-9 | Repo selector, branch display, refresh button, VCS listener |
| CLI | 10-12 | Accept `--review` flag, default to branch-based path |
| Verify | 13 | Integration testing |
