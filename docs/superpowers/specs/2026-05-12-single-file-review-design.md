# Single-File Review — Design

**Status:** approved, ready for implementation plan
**Date:** 2026-05-12

## Goal

Let a user right-click any file (typically a spec or doc) in the JetBrains Project view, leave inline review comments, and hand them off to an AI agent for a single pass of edits. The flow is parallel to today's branch-based review — it shares the agent process plumbing and the CLI/skill comment-handoff protocol, but lives in its own model, tool window, and skill so the git-shaped main flow stays unchanged.

## Non-Goals

- Iterating: comments are addressed in a single agent pass; the user does not re-review after the first round.
- Diff-based review of a single file. (This is content review, not change review.)
- Cross-file refactors driven from a file review. The agent stays focused on the one file in scope, though it may read the rest of the repo for context.

## User Flow

1. User right-clicks `docs/foo-spec.md` in the Project view → **Review File with Review Duet**.
2. The **Review Duet Files** tool window opens (creating it on first use), focused on a new or resumed review for that path.
3. User adds inline comments through the existing `CommentPopup` UI.
4. User clicks **Process with agent**. The configured agent runs the new `review-file` skill, edits the file, and marks each comment fixed via the CLI.
5. User reviews the edits in the editor and clicks **Accept & close**; the JSON is deleted and the entry leaves the tool window.

## Architecture

### New components

| Component | Path | Purpose |
| --- | --- | --- |
| `ReviewFileAction` | `plugin/.../actions/ReviewFileAction.kt` | Project-view popup action; resolves selected file + repo root; opens or creates the file review |
| `FileReview` | `plugin/.../model/FileReview.kt` | Domain object for one file review; owns the JSON path and exposes load/save/refresh |
| `FileReviewData` | `plugin/.../model/FileReviewData.kt` | Plain data record: `mode`, `filePath`, `fileHashAtStart`, `lastSeenHash`, `createdAt`, `comments[]` |
| `FileReviewDataSerializer` | `plugin/.../model/FileReviewDataSerializer.kt` | JSON ser/de |
| `FileReviewService` | `plugin/.../services/FileReviewService.kt` | Project-level `@Service`; map `Path → FileReview`; rehydrates on startup; persists on mutation |
| `CommentAnchorResolver` | `plugin/.../services/CommentAnchorResolver.kt` | Re-anchors comments when the file has changed (see "Line Stability" below) |
| `FileReviewToolWindowFactory` | `plugin/.../ui/FileReviewToolWindowFactory.kt` | Tool window registration; tool window id `Review Duet Files` |
| `FileReviewPanel` | `plugin/.../ui/FileReviewPanel.kt` | UI for the new tool window |
| `review-file` skill | `skills/review-file/` | Markdown skill driving the one-pass agent processing model |

### Reused, unchanged

- `AgentProcessService` — already keys running processes by the review-file path argument, so a branch review and a file review can run concurrently without collision.
- `AgentRegistry` / `AgentProvider` — agent abstraction is already path-agnostic. `AgentProvider.buildCommand(workingDir, reviewFilePath, skillContent)` already takes pre-loaded skill content, so the new flow stays inside the existing contract: the caller (`AgentProcessService.startFileReview(...)` or a new variant) loads `skills/review-file/` instead of the branch-review skill and hands the resulting `skillContent` to the same `buildCommand`. Providers do not change.
- `CommentPopup` — comment authoring UI is reused as-is.
- CLI (`review-duet list/show/fix/accept`) — operates on whichever review JSON it is pointed at via `--review <path>`; works on the new schema with `mode: "file"`.

### Branch-review code

Untouched. `Review`, `ReviewPanel`, `ReviewToolWindowFactory`, and the `Review Duet` tool window are not modified.

## Data Model

### Storage

- Directory: `.review-duet/files/` (sibling of branch reviews at `.review-duet/<branch>.json`).
- One JSON per active file review, filename derived from the file path with each `/` and `%` percent-encoded (`%2F`, `%25`) so distinct paths can't collide (`docs/foo.md` → `docs%2Ffoo.md.json` ≠ `docs_foo.md.json`).
- One active review per file; reopening from the right-click action reuses the existing JSON.

### Schema

```jsonc
{
  "mode": "file",
  "filePath": "docs/foo-spec.md",
  "fileHashAtStart": "sha1...",
  "lastSeenHash": "sha1...",
  "createdAt": "2026-05-12T10:00:00Z",
  "comments": [
    {
      "id": "c1",
      "line": 42,
      "lineText": "## Authentication flow",
      "contextBefore": ["", "Previous section"],
      "contextAfter": ["", "We use OAuth..."],
      "text": "this section needs a diagram",
      "status": "open",
      "createdAt": "2026-05-12T10:01:00Z"
    }
  ]
}
```

`status` values: `open`, `fixed`, `outdated`.

The `mode` field discriminates file reviews from branch reviews; absence of `mode` (or `mode: "branch"`) keeps the existing semantics. The CLI and skill branch on this field.

## Line Stability

There is no diff baseline, so line numbers are inherently mobile. Three layers:

**A — capture at comment time.** Each comment stores its `lineText` and up to two lines of `contextBefore` / `contextAfter`.

**B — re-anchor on every panel refresh.**

1. Compute current file hash. If it matches `fileHashAtStart`, line numbers are valid as-is.
2. Otherwise, for each comment, search the current file for `lineText`:
   - Unique match at the same line → still valid.
   - Unique match at a different line → re-anchor; flag as "moved" in the UI.
   - Multiple matches → use `contextBefore` / `contextAfter` to disambiguate.
   - Still ambiguous or no match → mark `status: outdated`; show the original `lineText` in the comment row.
3. Update `lastSeenHash` to the current hash.

**C — live tracking while the document is open.** When `FileReviewPanel` is showing a review whose file is open in an editor, attach an IntelliJ `RangeMarker` to each comment via `Document.createRangeMarker(...)`. The platform shifts the marker through user edits. On document save (or panel close) the new line number is written back to the JSON.

The re-anchoring code lives in `CommentAnchorResolver` and is the unit-tested core.

## Skill (`review-file`)

A new skill, processing model distinct from the branch-review skill:

- Receives `--review <path>` to the file-review JSON, same convention as today.
- One agent reads every open comment and edits the file in a single pass. No triage agent, no per-comment subagents.
- No commits — the agent leaves the file dirty for the user to review the edits in the IDE.
- For each addressed comment, the agent calls `review-duet fix <id> "<explanation>"` via the existing CLI to mark it `fixed`.

The skill's prompt should make the constraints explicit: edit the file at the indicated lines, address every open comment, do not commit, do not touch other files.

## Coexistence with Branch Reviews

- Branch JSON: `.review-duet/<branch>.json` — unchanged.
- File JSON: `.review-duet/files/*.json` — new.
- `AgentProcessService.start(reviewFilePath, repoRoot)` already keys processes by `reviewFilePath`, so the two flows never collide.
- Two tool windows: existing `Review Duet` (branch), new `Review Duet Files` (file). The user can drag-tab them together.

## Naming

| Thing | Name |
| --- | --- |
| Action class | `ReviewFileAction` |
| Action id | `CodeReview.ReviewFile` |
| Menu text | `Review File with Review Duet` |
| Model class | `FileReview` |
| Data record | `FileReviewData` |
| Serializer | `FileReviewDataSerializer` |
| Service | `FileReviewService` |
| Anchor resolver | `CommentAnchorResolver` |
| Tool window id | `Review Duet Files` |
| Tool window factory | `FileReviewToolWindowFactory` |
| Panel | `FileReviewPanel` |
| Storage dir | `.review-duet/files/` |
| Skill | `skills/review-file/` |

## Error Handling

- **File deleted/renamed while review is active** — `FileReview.refresh()` detects the missing path; panel shows a banner "File no longer exists" with `Accept & close` as the only action. Rename is reported as delete (no detection of the new path in v1).
- **File outside any repo root** — disable the menu action when `GitService.findRepoRoot` returns null and there is no project base path that can host `.review-duet/`.
- **Binary or oversize file** — disable the menu action for non-text files (`FileTypeRegistry.isFileOfType` binary check) and for files over 1 MB; surface a balloon stating why.
- **Corrupt JSON on load** — log and show an error banner in the tool window; do not silently overwrite. Recovery is manual deletion of the JSON.
- **Concurrent external file edit during agent processing** — `VirtualFileListener` triggers a panel refresh, which re-runs `CommentAnchorResolver`. No special-cased blocking.
- **Agent process failure** — reused notification path from `AgentProcessService`; no new error UI.

## Testing

### CLI (`cli/`, vitest)

- `review-duet fix <id> "<expl>"` against a file-mode JSON.
- `review-duet list` / `show` against a file-mode JSON — schema-tolerant of missing `baseCommit`.

### Plugin (`plugin/`, JUnit + IntelliJ test fixtures)

- `FileReviewDataSerializer` round-trip across all `status` values, with and without context lines.
- `CommentAnchorResolver` — valid, moved, ambiguous (disambiguated by context), and outdated cases.
- Hash computation stability across whitespace-equivalent files.
- `FileReviewService.openOrCreate` — creates new, returns existing, handles corrupt JSON.

### Skill

Manual smoke test against a sample doc with a handful of comments. The CLI surface the skill drives is already covered above.
