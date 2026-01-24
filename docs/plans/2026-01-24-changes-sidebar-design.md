# Changes Sidebar Design

## Overview

Add a "Changes" tab to the Review tool window that shows files changed between review rounds, similar to the VCS commit sidebar.

## Requirements

- Show list of files changed between any two review rounds
- User can select which rounds to compare via dropdowns
- Clicking a file opens the native IntelliJ diff viewer
- Structure should support future "mark file as reviewed" feature

## UI Design

### Tabbed Interface

The ReviewPanel gains a `JBTabbedPane` with two tabs:
- **Comments** - Existing comment list functionality
- **Changes** - New file changes view

### Changes Tab Layout

```
┌─────────────────────────────────────┐
│ From: [review-r0 ▼]  To: [review-r1 ▼] │
├─────────────────────────────────────┤
│ M  src/main/Example.kt              │
│ A  src/main/NewFile.kt              │
│ D  src/test/OldTest.kt              │
│ M  build.gradle.kts                 │
│                                     │
│                                     │
└─────────────────────────────────────┘
```

- **Dropdowns**: Populated from existing `review-r*` git tags
- **File list**: Shows change type (A=Added, M=Modified, D=Deleted), file path
- **Click action**: Opens side-by-side diff in native IntelliJ diff editor

## Technical Design

### New Files

| File | Purpose |
|------|---------|
| `ui/ChangesPanel.kt` | Panel with ref dropdowns and file list |

### Modified Files

| File | Changes |
|------|---------|
| `ui/ReviewPanel.kt` | Wrap content in JBTabbedPane |
| `services/GitService.kt` | Add methods for diff operations |

### GitService Additions

```kotlin
// Get list of changed files between two refs
fun getChangedFiles(fromRef: String, toRef: String): List<ChangedFile>

// Get list of review tags (review-r0, review-r1, etc.)
fun getReviewTags(): List<String>
```

### Data Model

```kotlin
data class ChangedFile(
    val path: String,
    val changeType: ChangeType  // ADDED, MODIFIED, DELETED
)

enum class ChangeType { ADDED, MODIFIED, DELETED }
```

### Diff Viewing

Use IntelliJ's native diff infrastructure:
1. Get file content at "from" ref via `git show fromRef:path`
2. Get file content at "to" ref via `git show toRef:path`
3. Create `SimpleDiffRequest` with both contents
4. Open via `DiffManager.getInstance().showDiff()`

## Future Extensibility

The custom `JBList` design allows easy addition of:
- Checkbox for "mark as reviewed" status
- Visual indicators (green checkmark, strikethrough)
- Persistence of review status in `.review/comments.json`

## Implementation Order

1. Add `ChangedFile` model and `ChangeType` enum
2. Add `getChangedFiles()` and `getReviewTags()` to GitService
3. Create `ChangesPanel` with dropdowns and file list
4. Add tabbed interface to ReviewPanel
5. Implement diff viewer integration
