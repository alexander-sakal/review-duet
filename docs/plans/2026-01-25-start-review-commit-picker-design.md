# Start Review with Commit Picker

## Overview

Replace "Start Feature Development" with "Start Review" and add a commit picker dropdown so users can start a review from any recent commit.

## UI Design

Replace the current "Start Feature Development" button with a stacked layout:

```
┌─────────────────────────────────┐
│ Select baseline commit:         │
│ ┌─────────────────────────────┐ │
│ │ Fix login bug (a1b2c3d)   ▼ │ │  ← ComboBox with recent commits
│ └─────────────────────────────┘ │
│ ┌─────────────────────────────┐ │
│ │       Start Review          │ │  ← Button
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

- Dropdown shows last 25 commits from current branch
- Each entry: truncated message (40 chars) + short SHA (7 chars)
- Most recent commit selected by default
- "Start Review" button creates the `review-r0` tag at the selected commit

## Implementation

### 1. Add CommitInfo data class

Location: `model/CommitInfo.kt`

```kotlin
data class CommitInfo(
    val sha: String,
    val shortSha: String,
    val message: String
) {
    val displayText: String
        get() = "${message.take(40)}${if (message.length > 40) "..." else ""} ($shortSha)"

    override fun toString() = displayText
}
```

### 2. GitService changes

Add method to get recent commits:

```kotlin
fun getRecentCommits(limit: Int = 25): List<CommitInfo>
```

Uses `git log --format="%H %s" -n 25` and parses output.

### 3. ReviewPanel changes

- Rename `startFeatureDevelopment()` → `startReview()`
- Replace `createStartButton()` with `createStartReviewPanel()`:
  - Label: "Select baseline commit:"
  - `JComboBox<CommitInfo>` populated from `GitService.getRecentCommits()`
  - "Start Review" button
- Button action uses selected commit's SHA for tag creation

### 4. Tag creation change

Update tag creation to use selected commit:
- Before: `git tag review-r0` (tags HEAD)
- After: `git tag review-r0 <selected-sha>`

## Files to Modify

1. `plugin/src/main/kotlin/com/codereview/local/model/CommitInfo.kt` (new)
2. `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`
3. `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`
