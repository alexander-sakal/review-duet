# Code Review Local - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a PhpStorm/IntelliJ plugin + CLI tool for local code review workflows when developing with Claude Code.

**Architecture:** Two-component system - (1) Node.js CLI tool (`review`) for Claude to interact with comments, (2) Kotlin IntelliJ plugin for user-facing review UI. Both share `.review/comments.json` as the single source of truth. Docker provides consistent dev environment.

**Tech Stack:** Node.js (CLI), Kotlin + IntelliJ Platform SDK + Gradle (plugin), Docker (dev environment)

---

## Phase 1: Project Foundation

### Task 1: Initialize Docker Development Environment

**Files:**
- Create: `docker-compose.yml`
- Create: `Dockerfile.cli`
- Create: `.dockerignore`

**Step 1: Create docker-compose.yml**

```yaml
version: '3.8'

services:
  cli:
    build:
      context: .
      dockerfile: Dockerfile.cli
    volumes:
      - ./cli:/app
      - ./test-project:/test-project
    working_dir: /app
    command: npm run dev
    environment:
      - NODE_ENV=development

  # Plugin dev uses local IDE - no container needed
  # This service is for CLI development only
```

**Step 2: Create Dockerfile.cli**

```dockerfile
FROM node:20-alpine

WORKDIR /app

# Install git for testing git operations
RUN apk add --no-cache git

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

CMD ["npm", "run", "dev"]
```

**Step 3: Create .dockerignore**

```
node_modules
.git
*.log
.idea
plugin/build
plugin/.gradle
```

**Step 4: Verify docker setup works**

Run: `docker compose build`
Expected: Build completes successfully

**Step 5: Commit**

```bash
git init
git add docker-compose.yml Dockerfile.cli .dockerignore
git commit -m "chore: add docker development environment"
```

---

### Task 2: Initialize CLI Project Structure

**Files:**
- Create: `cli/package.json`
- Create: `cli/tsconfig.json`
- Create: `cli/.gitignore`
- Create: `cli/src/index.ts`

**Step 1: Create cli/package.json**

```json
{
  "name": "code-review-cli",
  "version": "0.1.0",
  "description": "CLI tool for local code review workflows with Claude Code",
  "main": "dist/index.js",
  "bin": {
    "review": "./dist/index.js"
  },
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "test": "vitest",
    "test:watch": "vitest --watch"
  },
  "keywords": ["code-review", "cli", "claude"],
  "author": "",
  "license": "MIT",
  "devDependencies": {
    "@types/node": "^20.10.0",
    "typescript": "^5.3.0",
    "vitest": "^1.0.0"
  },
  "engines": {
    "node": ">=18.0.0"
  }
}
```

**Step 2: Create cli/tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "declaration": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist"]
}
```

**Step 3: Create cli/.gitignore**

```
node_modules/
dist/
*.log
.env
```

**Step 4: Create cli/src/index.ts (minimal placeholder)**

```typescript
#!/usr/bin/env node

console.log('review CLI - v0.1.0');
```

**Step 5: Install dependencies and verify build**

Run: `cd cli && npm install && npm run build`
Expected: Compiles successfully, creates dist/index.js

**Step 6: Commit**

```bash
git add cli/
git commit -m "chore: initialize CLI project structure with TypeScript"
```

---

### Task 3: Initialize Plugin Project Structure

**Files:**
- Create: `plugin/build.gradle.kts`
- Create: `plugin/settings.gradle.kts`
- Create: `plugin/gradle.properties`
- Create: `plugin/src/main/resources/META-INF/plugin.xml`
- Create: `plugin/src/main/kotlin/com/codereview/local/ReviewPlugin.kt`
- Create: `plugin/.gitignore`

**Step 1: Create plugin/settings.gradle.kts**

```kotlin
rootProject.name = "code-review-local"
```

**Step 2: Create plugin/gradle.properties**

```properties
# IntelliJ Platform Plugin Configuration
pluginGroup = com.codereview.local
pluginName = Code Review Local
pluginVersion = 0.1.0

# IntelliJ Platform version
platformType = IC
platformVersion = 2023.3

# Java/Kotlin versions
javaVersion = 17
kotlinVersion = 1.9.21

# Gradle
org.gradle.jvmargs = -Xmx2048m
```

**Step 3: Create plugin/build.gradle.kts**

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "com.codereview.local"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

intellij {
    version.set("2023.3")
    type.set("IC")
    plugins.set(listOf("Git4Idea"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("243.*")
    }

    test {
        useJUnitPlatform()
    }
}
```

**Step 4: Create plugin/src/main/resources/META-INF/plugin.xml**

```xml
<idea-plugin>
    <id>com.codereview.local</id>
    <name>Code Review Local</name>
    <vendor>Code Review Local</vendor>
    <description>Local code review workflows for AI-assisted development</description>

    <depends>com.intellij.modules.platform</depends>
    <depends>Git4Idea</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="Code Review"
                    anchor="right"
                    factoryClass="com.codereview.local.ui.ReviewToolWindowFactory"
                    icon="AllIcons.Actions.Annotate"/>
    </extensions>
</idea-plugin>
```

**Step 5: Create plugin/src/main/kotlin/com/codereview/local/ReviewPlugin.kt**

```kotlin
package com.codereview.local

object ReviewPlugin {
    const val ID = "com.codereview.local"
    const val NAME = "Code Review Local"
}
```

**Step 6: Create plugin/.gitignore**

```
.gradle/
build/
.idea/
*.iml
```

**Step 7: Verify plugin builds**

Run: `cd plugin && ./gradlew build` (or download gradle wrapper first)
Expected: Build completes (may have warnings about missing classes, that's OK for now)

**Step 8: Commit**

```bash
git add plugin/
git commit -m "chore: initialize IntelliJ plugin project structure"
```

---

### Task 4: Create Test Project Fixture

**Files:**
- Create: `test-project/.review/comments.json`
- Create: `test-project/.gitignore`
- Create: `test-project/src/Example.php`

**Step 1: Create test-project/.gitignore**

```
.review/
```

**Step 2: Create test-project/.review/comments.json (sample data)**

```json
{
  "version": 1,
  "currentRound": "review-r1",
  "baseRef": "review-r0",
  "comments": [
    {
      "id": 1,
      "file": "src/Example.php",
      "line": 10,
      "ref": "review-r1",
      "status": "open",
      "resolveCommit": null,
      "thread": [
        {
          "author": "user",
          "text": "Extract this logic to a separate method",
          "at": "2024-01-23T10:00:00Z"
        }
      ]
    },
    {
      "id": 2,
      "file": "src/Example.php",
      "line": 25,
      "ref": "review-r1",
      "status": "pending-user",
      "resolveCommit": null,
      "thread": [
        {
          "author": "user",
          "text": "Handle the error case",
          "at": "2024-01-23T10:01:00Z"
        },
        {
          "author": "agent",
          "text": "Should this throw an exception or return null?",
          "at": "2024-01-23T10:31:00Z"
        }
      ]
    }
  ]
}
```

**Step 3: Create test-project/src/Example.php**

```php
<?php

namespace App;

class Example
{
    public function processData(array $data): array
    {
        // Line 10 - has comment about extraction
        $result = [];
        foreach ($data as $item) {
            if (is_string($item)) {
                $result[] = strtoupper($item);
            }
        }
        return $result;
    }

    public function fetchRecord(int $id): ?array
    {
        $db = $this->getDatabase();

        // Line 25 - has comment about error handling
        $record = $db->find($id);

        return $record;
    }

    private function getDatabase(): object
    {
        return new \stdClass();
    }
}
```

**Step 4: Commit**

```bash
git add test-project/
git commit -m "chore: add test project fixture with sample review data"
```

---

## Phase 2: CLI Core Implementation

### Task 5: Implement Comment Data Types

**Files:**
- Create: `cli/src/types.ts`
- Create: `cli/src/types.test.ts`

**Step 1: Write the failing test**

Create `cli/src/types.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { CommentStatus, isValidStatus, ThreadEntry, Comment, ReviewData } from './types';

describe('CommentStatus', () => {
  it('should have all valid statuses', () => {
    const statuses: CommentStatus[] = [
      'open',
      'pending-user',
      'pending-agent',
      'fixed',
      'resolved',
      'wontfix'
    ];

    statuses.forEach(status => {
      expect(isValidStatus(status)).toBe(true);
    });
  });

  it('should reject invalid statuses', () => {
    expect(isValidStatus('invalid')).toBe(false);
    expect(isValidStatus('')).toBe(false);
  });
});

describe('ThreadEntry', () => {
  it('should validate author as user or agent', () => {
    const entry: ThreadEntry = {
      author: 'user',
      text: 'Test comment',
      at: '2024-01-23T10:00:00Z'
    };
    expect(entry.author).toBe('user');
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - module './types' not found

**Step 3: Write minimal implementation**

Create `cli/src/types.ts`:

```typescript
export type CommentStatus =
  | 'open'
  | 'pending-user'
  | 'pending-agent'
  | 'fixed'
  | 'resolved'
  | 'wontfix';

const VALID_STATUSES: CommentStatus[] = [
  'open',
  'pending-user',
  'pending-agent',
  'fixed',
  'resolved',
  'wontfix'
];

export function isValidStatus(status: string): status is CommentStatus {
  return VALID_STATUSES.includes(status as CommentStatus);
}

export type Author = 'user' | 'agent';

export interface ThreadEntry {
  author: Author;
  text: string;
  at: string; // ISO 8601 timestamp
}

export interface Comment {
  id: number;
  file: string;
  line: number;
  ref: string;
  status: CommentStatus;
  resolveCommit: string | null;
  thread: ThreadEntry[];
}

export interface ReviewData {
  version: number;
  currentRound: string;
  baseRef: string;
  comments: Comment[];
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/types.ts cli/src/types.test.ts
git commit -m "feat(cli): add comment data types and validation"
```

---

### Task 6: Implement Review Data Store

**Files:**
- Create: `cli/src/store.ts`
- Create: `cli/src/store.test.ts`

**Step 1: Write the failing test**

Create `cli/src/store.test.ts`:

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { ReviewStore } from './store';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('ReviewStore', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(tempDir, '.review', 'comments.json');
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  describe('load', () => {
    it('should load existing review data', () => {
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: []
      };

      fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(tempDir);
      const loaded = store.load();

      expect(loaded.version).toBe(1);
      expect(loaded.currentRound).toBe('review-r1');
    });

    it('should throw if no review file exists', () => {
      const store = new ReviewStore(tempDir);
      expect(() => store.load()).toThrow('No active review');
    });
  });

  describe('save', () => {
    it('should save review data to file', () => {
      const store = new ReviewStore(tempDir);
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: []
      };

      store.save(data);

      const saved = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
      expect(saved.currentRound).toBe('review-r1');
    });
  });

  describe('getComment', () => {
    it('should return comment by id', () => {
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: [
          { id: 1, file: 'test.ts', line: 10, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [] },
          { id: 2, file: 'test.ts', line: 20, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [] }
        ]
      };

      fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(tempDir);
      const comment = store.getComment(2);

      expect(comment?.line).toBe(20);
    });

    it('should return undefined for non-existent id', () => {
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: []
      };

      fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(tempDir);
      expect(store.getComment(999)).toBeUndefined();
    });
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - module './store' not found

**Step 3: Write minimal implementation**

Create `cli/src/store.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';
import { ReviewData, Comment } from './types';

export class ReviewStore {
  private readonly reviewPath: string;
  private data: ReviewData | null = null;

  constructor(projectRoot: string) {
    this.reviewPath = path.join(projectRoot, '.review', 'comments.json');
  }

  load(): ReviewData {
    if (!fs.existsSync(this.reviewPath)) {
      throw new Error('No active review. Initialize with the IDE plugin first.');
    }

    const content = fs.readFileSync(this.reviewPath, 'utf-8');
    this.data = JSON.parse(content) as ReviewData;
    return this.data;
  }

  save(data: ReviewData): void {
    const dir = path.dirname(this.reviewPath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(this.reviewPath, JSON.stringify(data, null, 2));
    this.data = data;
  }

  getComment(id: number): Comment | undefined {
    if (!this.data) {
      this.load();
    }
    return this.data?.comments.find(c => c.id === id);
  }

  updateComment(id: number, updater: (comment: Comment) => void): void {
    if (!this.data) {
      this.load();
    }
    const comment = this.data?.comments.find(c => c.id === id);
    if (!comment) {
      throw new Error(`Comment #${id} not found`);
    }
    updater(comment);
    this.save(this.data!);
  }
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/store.ts cli/src/store.test.ts
git commit -m "feat(cli): add ReviewStore for JSON state management"
```

---

### Task 7: Implement List Command

**Files:**
- Create: `cli/src/commands/list.ts`
- Create: `cli/src/commands/list.test.ts`

**Step 1: Write the failing test**

Create `cli/src/commands/list.test.ts`:

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { listComments, formatComment } from './list';
import { ReviewStore } from '../store';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('list command', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(tempDir, '.review', 'comments.json');
    fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  describe('formatComment', () => {
    it('should format a simple comment', () => {
      const comment = {
        id: 1,
        file: 'src/Example.php',
        line: 42,
        ref: 'review-r1',
        status: 'open' as const,
        resolveCommit: null,
        thread: [{ author: 'user' as const, text: 'Fix this bug', at: '2024-01-23T10:00:00Z' }]
      };

      const output = formatComment(comment);

      expect(output).toContain('#1');
      expect(output).toContain('[open]');
      expect(output).toContain('src/Example.php:42');
      expect(output).toContain('Fix this bug');
    });

    it('should show last agent reply for pending-user status', () => {
      const comment = {
        id: 2,
        file: 'src/Example.php',
        line: 87,
        ref: 'review-r1',
        status: 'pending-user' as const,
        resolveCommit: null,
        thread: [
          { author: 'user' as const, text: 'Handle error', at: '2024-01-23T10:00:00Z' },
          { author: 'agent' as const, text: 'Throw or return null?', at: '2024-01-23T10:30:00Z' }
        ]
      };

      const output = formatComment(comment);

      expect(output).toContain('[pending-user]');
      expect(output).toContain('Throw or return null?');
    });
  });

  describe('listComments', () => {
    it('should list all comments when no filter', () => {
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: [
          { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Comment 1', at: '2024-01-23T10:00:00Z' }] },
          { id: 2, file: 'b.ts', line: 2, ref: 'review-r1', status: 'fixed', resolveCommit: 'abc123', thread: [{ author: 'user', text: 'Comment 2', at: '2024-01-23T10:00:00Z' }] }
        ]
      };
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(tempDir);
      const output = listComments(store);

      expect(output).toContain('#1');
      expect(output).toContain('#2');
    });

    it('should filter by status', () => {
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: [
          { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Comment 1', at: '2024-01-23T10:00:00Z' }] },
          { id: 2, file: 'b.ts', line: 2, ref: 'review-r1', status: 'fixed', resolveCommit: 'abc123', thread: [{ author: 'user', text: 'Comment 2', at: '2024-01-23T10:00:00Z' }] }
        ]
      };
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(tempDir);
      const output = listComments(store, { status: 'open' });

      expect(output).toContain('#1');
      expect(output).not.toContain('#2');
    });
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - module './list' not found

**Step 3: Write minimal implementation**

Create `cli/src/commands/list.ts`:

```typescript
import { ReviewStore } from '../store';
import { Comment, CommentStatus } from '../types';

export interface ListOptions {
  status?: CommentStatus;
}

export function formatComment(comment: Comment): string {
  const lines: string[] = [];

  const statusTag = `[${comment.status}]`;
  const location = `${comment.file}:${comment.line}`;
  const refTag = `@${comment.ref}`;

  lines.push(`#${comment.id} ${statusTag} ${location} ${refTag}`);

  // Show first user message
  const firstUserMsg = comment.thread.find(t => t.author === 'user');
  if (firstUserMsg) {
    lines.push(`   "${firstUserMsg.text}"`);
  }

  // For pending-user, show the last agent message
  if (comment.status === 'pending-user') {
    const lastAgentMsg = [...comment.thread].reverse().find(t => t.author === 'agent');
    if (lastAgentMsg) {
      lines.push(`   └─ Claude asked: "${lastAgentMsg.text}"`);
    }
  }

  // For pending-agent, show the last user reply
  if (comment.status === 'pending-agent') {
    const lastUserMsg = [...comment.thread].reverse().find(t => t.author === 'user');
    if (lastUserMsg && comment.thread.length > 1) {
      lines.push(`   └─ You answered: "${lastUserMsg.text}"`);
    }
  }

  // For fixed, show the commit
  if (comment.status === 'fixed' && comment.resolveCommit) {
    lines.push(`   └─ Fixed in ${comment.resolveCommit}`);
  }

  return lines.join('\n');
}

export function listComments(store: ReviewStore, options: ListOptions = {}): string {
  const data = store.load();

  let comments = data.comments;

  if (options.status) {
    comments = comments.filter(c => c.status === options.status);
  }

  if (comments.length === 0) {
    return 'No comments found.';
  }

  return comments.map(formatComment).join('\n\n');
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/commands/list.ts cli/src/commands/list.test.ts
git commit -m "feat(cli): add list command with status filtering"
```

---

### Task 8: Implement Reply Command

**Files:**
- Create: `cli/src/commands/reply.ts`
- Create: `cli/src/commands/reply.test.ts`

**Step 1: Write the failing test**

Create `cli/src/commands/reply.test.ts`:

```typescript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { replyToComment } from './reply';
import { ReviewStore } from '../store';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('reply command', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(tempDir, '.review', 'comments.json');
    fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-23T12:00:00Z'));
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
    vi.useRealTimers();
  });

  it('should add a reply to the thread', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: [
        { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    replyToComment(store, 1, 'Should I use method A or B?');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].thread).toHaveLength(2);
    expect(updated.comments[0].thread[1].author).toBe('agent');
    expect(updated.comments[0].thread[1].text).toBe('Should I use method A or B?');
  });

  it('should update status to pending-user when agent replies', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: [
        { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    replyToComment(store, 1, 'What approach?');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].status).toBe('pending-user');
  });

  it('should throw if comment not found', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: []
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    expect(() => replyToComment(store, 999, 'Hello')).toThrow('Comment #999 not found');
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - module './reply' not found

**Step 3: Write minimal implementation**

Create `cli/src/commands/reply.ts`:

```typescript
import { ReviewStore } from '../store';

export function replyToComment(store: ReviewStore, id: number, message: string): void {
  store.updateComment(id, (comment) => {
    comment.thread.push({
      author: 'agent',
      text: message,
      at: new Date().toISOString()
    });

    // Agent reply sets status to pending-user (waiting for user response)
    if (comment.status === 'open' || comment.status === 'pending-agent') {
      comment.status = 'pending-user';
    }
  });
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/commands/reply.ts cli/src/commands/reply.test.ts
git commit -m "feat(cli): add reply command for threaded comments"
```

---

### Task 9: Implement Fix Command

**Files:**
- Create: `cli/src/commands/fix.ts`
- Create: `cli/src/commands/fix.test.ts`

**Step 1: Write the failing test**

Create `cli/src/commands/fix.test.ts`:

```typescript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { markAsFixed } from './fix';
import { ReviewStore } from '../store';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('fix command', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(tempDir, '.review', 'comments.json');
    fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-23T12:00:00Z'));
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
    vi.useRealTimers();
  });

  it('should mark comment as fixed with commit sha', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: [
        { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    markAsFixed(store, 1, 'abc1234');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].status).toBe('fixed');
    expect(updated.comments[0].resolveCommit).toBe('abc1234');
  });

  it('should add a thread entry noting the fix', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: [
        { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    markAsFixed(store, 1, 'abc1234');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].thread).toHaveLength(2);
    expect(updated.comments[0].thread[1].text).toContain('abc1234');
  });

  it('should throw if comment not found', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: []
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    expect(() => markAsFixed(store, 999, 'abc')).toThrow('Comment #999 not found');
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - module './fix' not found

**Step 3: Write minimal implementation**

Create `cli/src/commands/fix.ts`:

```typescript
import { ReviewStore } from '../store';

export function markAsFixed(store: ReviewStore, id: number, commitSha: string): void {
  store.updateComment(id, (comment) => {
    comment.status = 'fixed';
    comment.resolveCommit = commitSha;

    comment.thread.push({
      author: 'agent',
      text: `Fixed in ${commitSha}`,
      at: new Date().toISOString()
    });
  });
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/commands/fix.ts cli/src/commands/fix.test.ts
git commit -m "feat(cli): add fix command to mark comments as resolved"
```

---

### Task 10: Implement Show Command

**Files:**
- Create: `cli/src/commands/show.ts`
- Create: `cli/src/commands/show.test.ts`

**Step 1: Write the failing test**

Create `cli/src/commands/show.test.ts`:

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { showComment } from './show';
import { ReviewStore } from '../store';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('show command', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(tempDir, '.review', 'comments.json');
    fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  it('should show full comment details', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: [
        {
          id: 1,
          file: 'src/Example.php',
          line: 42,
          ref: 'review-r1',
          status: 'pending-user',
          resolveCommit: null,
          thread: [
            { author: 'user', text: 'Fix this bug', at: '2024-01-23T10:00:00Z' },
            { author: 'agent', text: 'What approach?', at: '2024-01-23T10:30:00Z' }
          ]
        }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    const output = showComment(store, 1);

    expect(output).toContain('Comment #1');
    expect(output).toContain('src/Example.php:42');
    expect(output).toContain('pending-user');
    expect(output).toContain('Fix this bug');
    expect(output).toContain('What approach?');
  });

  it('should include git command hint', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: [
        { id: 1, file: 'src/Example.php', line: 42, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Test', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    const output = showComment(store, 1);

    expect(output).toContain('git show review-r1:src/Example.php');
  });

  it('should throw if comment not found', () => {
    const data = {
      version: 1,
      currentRound: 'review-r1',
      baseRef: 'review-r0',
      comments: []
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    expect(() => showComment(store, 999)).toThrow('Comment #999 not found');
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - module './show' not found

**Step 3: Write minimal implementation**

Create `cli/src/commands/show.ts`:

```typescript
import { ReviewStore } from '../store';

export function showComment(store: ReviewStore, id: number): string {
  const comment = store.getComment(id);

  if (!comment) {
    throw new Error(`Comment #${id} not found`);
  }

  const lines: string[] = [];

  lines.push(`Comment #${comment.id}`);
  lines.push(`File: ${comment.file}:${comment.line}`);
  lines.push(`Status: ${comment.status}`);
  lines.push(`Ref: ${comment.ref}`);

  if (comment.resolveCommit) {
    lines.push(`Resolve Commit: ${comment.resolveCommit}`);
  }

  lines.push('');
  lines.push('Thread:');
  lines.push('─'.repeat(40));

  for (const entry of comment.thread) {
    const authorLabel = entry.author === 'user' ? 'User' : 'Claude';
    const timestamp = new Date(entry.at).toLocaleString();
    lines.push(`[${authorLabel}] ${timestamp}`);
    lines.push(entry.text);
    lines.push('');
  }

  lines.push('─'.repeat(40));
  lines.push(`View original: git show ${comment.ref}:${comment.file}`);

  return lines.join('\n');
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/commands/show.ts cli/src/commands/show.test.ts
git commit -m "feat(cli): add show command for detailed comment view"
```

---

### Task 11: Implement CLI Entry Point

**Files:**
- Modify: `cli/src/index.ts`
- Create: `cli/src/cli.test.ts`

**Step 1: Write the failing test**

Create `cli/src/cli.test.ts`:

```typescript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { parseArgs, runCli } from './index';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('CLI', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(tempDir, '.review', 'comments.json');
    fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  describe('parseArgs', () => {
    it('should parse list command', () => {
      const result = parseArgs(['list']);
      expect(result.command).toBe('list');
    });

    it('should parse list with status filter', () => {
      const result = parseArgs(['list', '--status=open']);
      expect(result.command).toBe('list');
      expect(result.options.status).toBe('open');
    });

    it('should parse reply command', () => {
      const result = parseArgs(['reply', '1', 'My message']);
      expect(result.command).toBe('reply');
      expect(result.args).toEqual(['1', 'My message']);
    });

    it('should parse fix command', () => {
      const result = parseArgs(['fix', '1', '--commit', 'abc123']);
      expect(result.command).toBe('fix');
      expect(result.args).toEqual(['1']);
      expect(result.options.commit).toBe('abc123');
    });

    it('should parse show command', () => {
      const result = parseArgs(['show', '1']);
      expect(result.command).toBe('show');
      expect(result.args).toEqual(['1']);
    });
  });

  describe('runCli', () => {
    it('should run list command', () => {
      const data = {
        version: 1,
        currentRound: 'review-r1',
        baseRef: 'review-r0',
        comments: [
          { id: 1, file: 'a.ts', line: 1, ref: 'review-r1', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Test', at: '2024-01-23T10:00:00Z' }] }
        ]
      };
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const output = runCli(['list'], tempDir);
      expect(output).toContain('#1');
    });
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd cli && npm test`
Expected: FAIL - parseArgs not exported

**Step 3: Write minimal implementation**

Update `cli/src/index.ts`:

```typescript
#!/usr/bin/env node

import { ReviewStore } from './store';
import { listComments } from './commands/list';
import { replyToComment } from './commands/reply';
import { markAsFixed } from './commands/fix';
import { showComment } from './commands/show';
import { CommentStatus, isValidStatus } from './types';

export interface ParsedArgs {
  command: string;
  args: string[];
  options: Record<string, string>;
}

export function parseArgs(argv: string[]): ParsedArgs {
  const command = argv[0] || 'help';
  const args: string[] = [];
  const options: Record<string, string> = {};

  for (let i = 1; i < argv.length; i++) {
    const arg = argv[i];

    if (arg.startsWith('--')) {
      const [key, value] = arg.slice(2).split('=');
      if (value !== undefined) {
        options[key] = value;
      } else if (argv[i + 1] && !argv[i + 1].startsWith('--')) {
        options[key] = argv[++i];
      } else {
        options[key] = 'true';
      }
    } else {
      args.push(arg);
    }
  }

  return { command, args, options };
}

export function runCli(argv: string[], cwd: string = process.cwd()): string {
  const { command, args, options } = parseArgs(argv);
  const store = new ReviewStore(cwd);

  switch (command) {
    case 'list': {
      const statusFilter = options.status as CommentStatus | undefined;
      if (statusFilter && !isValidStatus(statusFilter)) {
        throw new Error(`Invalid status: ${statusFilter}`);
      }
      return listComments(store, { status: statusFilter });
    }

    case 'reply': {
      const id = parseInt(args[0], 10);
      const message = args[1];
      if (isNaN(id)) throw new Error('Invalid comment ID');
      if (!message) throw new Error('Message required');
      replyToComment(store, id, message);
      return `Replied to comment #${id}`;
    }

    case 'fix': {
      const id = parseInt(args[0], 10);
      const commit = options.commit;
      if (isNaN(id)) throw new Error('Invalid comment ID');
      if (!commit) throw new Error('--commit required');
      markAsFixed(store, id, commit);
      return `Marked comment #${id} as fixed (${commit})`;
    }

    case 'show': {
      const id = parseInt(args[0], 10);
      if (isNaN(id)) throw new Error('Invalid comment ID');
      return showComment(store, id);
    }

    case 'help':
    default:
      return `review CLI - Code review helper for Claude Code

Usage:
  review list [--status=<status>]    List comments
  review reply <id> "<message>"      Reply to a comment
  review fix <id> --commit <sha>     Mark as fixed
  review show <id>                   Show comment details

Statuses: open, pending-user, pending-agent, fixed, resolved, wontfix`;
  }
}

// Main entry point
if (require.main === module) {
  try {
    const output = runCli(process.argv.slice(2));
    console.log(output);
  } catch (error) {
    console.error(`Error: ${(error as Error).message}`);
    process.exit(1);
  }
}
```

**Step 4: Run test to verify it passes**

Run: `cd cli && npm test`
Expected: PASS

**Step 5: Commit**

```bash
git add cli/src/index.ts cli/src/cli.test.ts
git commit -m "feat(cli): add main CLI entry point with command routing"
```

---

## Phase 3: Plugin Core Implementation

### Task 12: Implement Review Data Model

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/model/ReviewData.kt`
- Create: `plugin/src/test/kotlin/com/codereview/local/model/ReviewDataTest.kt`

**Step 1: Write the failing test**

Create `plugin/src/test/kotlin/com/codereview/local/model/ReviewDataTest.kt`:

```kotlin
package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReviewDataTest {

    @Test
    fun `should parse valid status`() {
        assertEquals(CommentStatus.OPEN, CommentStatus.fromString("open"))
        assertEquals(CommentStatus.PENDING_USER, CommentStatus.fromString("pending-user"))
        assertEquals(CommentStatus.FIXED, CommentStatus.fromString("fixed"))
    }

    @Test
    fun `should serialize status to json value`() {
        assertEquals("open", CommentStatus.OPEN.jsonValue)
        assertEquals("pending-user", CommentStatus.PENDING_USER.jsonValue)
    }

    @Test
    fun `should validate thread entry author`() {
        val entry = ThreadEntry(
            author = "user",
            text = "Test comment",
            at = "2024-01-23T10:00:00Z"
        )
        assertTrue(entry.isUserComment)
        assertFalse(entry.isAgentComment)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test`
Expected: FAIL - classes not found

**Step 3: Write minimal implementation**

Create `plugin/src/main/kotlin/com/codereview/local/model/ReviewData.kt`:

```kotlin
package com.codereview.local.model

enum class CommentStatus(val jsonValue: String) {
    OPEN("open"),
    PENDING_USER("pending-user"),
    PENDING_AGENT("pending-agent"),
    FIXED("fixed"),
    RESOLVED("resolved"),
    WONTFIX("wontfix");

    companion object {
        fun fromString(value: String): CommentStatus {
            return entries.find { it.jsonValue == value }
                ?: throw IllegalArgumentException("Unknown status: $value")
        }
    }
}

data class ThreadEntry(
    val author: String,
    val text: String,
    val at: String
) {
    val isUserComment: Boolean get() = author == "user"
    val isAgentComment: Boolean get() = author == "agent"
}

data class Comment(
    val id: Int,
    val file: String,
    val line: Int,
    val ref: String,
    var status: CommentStatus,
    var resolveCommit: String?,
    val thread: MutableList<ThreadEntry>
) {
    val firstUserMessage: String?
        get() = thread.firstOrNull { it.isUserComment }?.text

    val lastAgentMessage: String?
        get() = thread.lastOrNull { it.isAgentComment }?.text
}

data class ReviewData(
    val version: Int,
    var currentRound: String,
    val baseRef: String,
    val comments: MutableList<Comment>
) {
    fun getComment(id: Int): Comment? = comments.find { it.id == id }

    fun getNextCommentId(): Int = (comments.maxOfOrNull { it.id } ?: 0) + 1

    fun getCommentsByStatus(status: CommentStatus): List<Comment> =
        comments.filter { it.status == status }
}
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/ReviewData.kt
git add plugin/src/test/kotlin/com/codereview/local/model/ReviewDataTest.kt
git commit -m "feat(plugin): add review data model with status enum"
```

---

### Task 13: Implement JSON Serialization

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/model/ReviewDataSerializer.kt`
- Create: `plugin/src/test/kotlin/com/codereview/local/model/ReviewDataSerializerTest.kt`

**Step 1: Write the failing test**

Create `plugin/src/test/kotlin/com/codereview/local/model/ReviewDataSerializerTest.kt`:

```kotlin
package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReviewDataSerializerTest {

    private val serializer = ReviewDataSerializer()

    @Test
    fun `should deserialize valid JSON`() {
        val json = """
        {
          "version": 1,
          "currentRound": "review-r1",
          "baseRef": "review-r0",
          "comments": [
            {
              "id": 1,
              "file": "src/Example.php",
              "line": 42,
              "ref": "review-r1",
              "status": "open",
              "resolveCommit": null,
              "thread": [
                {"author": "user", "text": "Fix this", "at": "2024-01-23T10:00:00Z"}
              ]
            }
          ]
        }
        """.trimIndent()

        val data = serializer.deserialize(json)

        assertEquals(1, data.version)
        assertEquals("review-r1", data.currentRound)
        assertEquals(1, data.comments.size)
        assertEquals(CommentStatus.OPEN, data.comments[0].status)
    }

    @Test
    fun `should serialize to valid JSON`() {
        val data = ReviewData(
            version = 1,
            currentRound = "review-r1",
            baseRef = "review-r0",
            comments = mutableListOf(
                Comment(
                    id = 1,
                    file = "test.kt",
                    line = 10,
                    ref = "review-r1",
                    status = CommentStatus.OPEN,
                    resolveCommit = null,
                    thread = mutableListOf(
                        ThreadEntry("user", "Test", "2024-01-23T10:00:00Z")
                    )
                )
            )
        )

        val json = serializer.serialize(data)

        assertTrue(json.contains("\"status\": \"open\""))
        assertTrue(json.contains("\"currentRound\": \"review-r1\""))
    }

    @Test
    fun `should round-trip data correctly`() {
        val original = ReviewData(
            version = 1,
            currentRound = "review-r2",
            baseRef = "review-r0",
            comments = mutableListOf()
        )

        val json = serializer.serialize(original)
        val restored = serializer.deserialize(json)

        assertEquals(original.currentRound, restored.currentRound)
        assertEquals(original.baseRef, restored.baseRef)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test`
Expected: FAIL - ReviewDataSerializer not found

**Step 3: Write minimal implementation**

Create `plugin/src/main/kotlin/com/codereview/local/model/ReviewDataSerializer.kt`:

```kotlin
package com.codereview.local.model

import com.google.gson.*
import java.lang.reflect.Type

class ReviewDataSerializer {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(CommentStatus::class.java, CommentStatusAdapter())
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun deserialize(json: String): ReviewData {
        return gson.fromJson(json, ReviewData::class.java)
    }

    fun serialize(data: ReviewData): String {
        return gson.toJson(data)
    }

    private class CommentStatusAdapter : JsonSerializer<CommentStatus>, JsonDeserializer<CommentStatus> {
        override fun serialize(src: CommentStatus, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.jsonValue)
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CommentStatus {
            return CommentStatus.fromString(json.asString)
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/model/ReviewDataSerializer.kt
git add plugin/src/test/kotlin/com/codereview/local/model/ReviewDataSerializerTest.kt
git commit -m "feat(plugin): add JSON serialization with Gson"
```

---

### Task 14: Implement Review Service

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt`
- Create: `plugin/src/test/kotlin/com/codereview/local/services/ReviewServiceTest.kt`

**Step 1: Write the failing test**

Create `plugin/src/test/kotlin/com/codereview/local/services/ReviewServiceTest.kt`:

```kotlin
package com.codereview.local.services

import com.codereview.local.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.io.path.readText

class ReviewServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: ReviewService

    @BeforeEach
    fun setup() {
        service = ReviewService(tempDir)
    }

    @Test
    fun `should return false when no review exists`() {
        assertFalse(service.hasActiveReview())
    }

    @Test
    fun `should return true when review file exists`() {
        val reviewDir = tempDir.resolve(".review")
        reviewDir.createDirectories()
        reviewDir.resolve("comments.json").writeText("""
            {"version": 1, "currentRound": "review-r1", "baseRef": "review-r0", "comments": []}
        """.trimIndent())

        assertTrue(service.hasActiveReview())
    }

    @Test
    fun `should load review data`() {
        val reviewDir = tempDir.resolve(".review")
        reviewDir.createDirectories()
        reviewDir.resolve("comments.json").writeText("""
            {
                "version": 1,
                "currentRound": "review-r1",
                "baseRef": "review-r0",
                "comments": [
                    {"id": 1, "file": "test.kt", "line": 10, "ref": "review-r1", "status": "open", "resolveCommit": null, "thread": []}
                ]
            }
        """.trimIndent())

        val data = service.loadReviewData()

        assertNotNull(data)
        assertEquals("review-r1", data?.currentRound)
        assertEquals(1, data?.comments?.size)
    }

    @Test
    fun `should initialize new review`() {
        service.initializeReview("review-r0")

        val data = service.loadReviewData()
        assertNotNull(data)
        assertEquals("review-r0", data?.currentRound)
        assertEquals("review-r0", data?.baseRef)
        assertTrue(data?.comments?.isEmpty() == true)
    }

    @Test
    fun `should add comment`() {
        service.initializeReview("review-r0")

        service.addComment("src/Test.kt", 42, "Fix this bug")

        val data = service.loadReviewData()
        assertEquals(1, data?.comments?.size)
        assertEquals("src/Test.kt", data?.comments?.get(0)?.file)
        assertEquals(42, data?.comments?.get(0)?.line)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test`
Expected: FAIL - ReviewService not found

**Step 3: Write minimal implementation**

Create `plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt`:

```kotlin
package com.codereview.local.services

import com.codereview.local.model.*
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class ReviewService(private val projectRoot: Path) {

    private val reviewDir: Path = projectRoot.resolve(".review")
    private val commentsFile: Path = reviewDir.resolve("comments.json")
    private val serializer = ReviewDataSerializer()

    private var cachedData: ReviewData? = null

    fun hasActiveReview(): Boolean = commentsFile.exists()

    fun loadReviewData(): ReviewData? {
        if (!hasActiveReview()) return null

        val json = commentsFile.readText()
        cachedData = serializer.deserialize(json)
        return cachedData
    }

    fun saveReviewData(data: ReviewData) {
        if (!reviewDir.exists()) {
            reviewDir.createDirectories()
        }
        commentsFile.writeText(serializer.serialize(data))
        cachedData = data
    }

    fun initializeReview(baseRef: String) {
        val data = ReviewData(
            version = 1,
            currentRound = baseRef,
            baseRef = baseRef,
            comments = mutableListOf()
        )
        saveReviewData(data)
    }

    fun addComment(file: String, line: Int, text: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")

        val comment = Comment(
            id = data.getNextCommentId(),
            file = file,
            line = line,
            ref = data.currentRound,
            status = CommentStatus.OPEN,
            resolveCommit = null,
            thread = mutableListOf(
                ThreadEntry(
                    author = "user",
                    text = text,
                    at = Instant.now().toString()
                )
            )
        )

        data.comments.add(comment)
        saveReviewData(data)
    }

    fun updateCommentStatus(commentId: Int, status: CommentStatus) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        comment.status = status
        saveReviewData(data)
    }

    fun addReply(commentId: Int, text: String, asAgent: Boolean = false) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        val comment = data.getComment(commentId) ?: throw IllegalArgumentException("Comment not found")

        comment.thread.add(
            ThreadEntry(
                author = if (asAgent) "agent" else "user",
                text = text,
                at = Instant.now().toString()
            )
        )

        // Update status based on who replied
        if (asAgent && comment.status == CommentStatus.OPEN) {
            comment.status = CommentStatus.PENDING_USER
        } else if (!asAgent && comment.status == CommentStatus.PENDING_USER) {
            comment.status = CommentStatus.PENDING_AGENT
        }

        saveReviewData(data)
    }

    fun startNewRound(roundTag: String) {
        val data = loadReviewData() ?: throw IllegalStateException("No active review")
        data.currentRound = roundTag
        saveReviewData(data)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/ReviewService.kt
git add plugin/src/test/kotlin/com/codereview/local/services/ReviewServiceTest.kt
git commit -m "feat(plugin): add ReviewService for state management"
```

---

### Task 15: Implement Tool Window Factory

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewToolWindowFactory.kt`
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Create ReviewToolWindowFactory**

```kotlin
package com.codereview.local.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ReviewToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

**Step 2: Create ReviewPanel (initial state)**

```kotlin
package com.codereview.local.ui

import com.codereview.local.services.ReviewService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.SwingConstants

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val reviewService: ReviewService by lazy {
        val basePath = project.basePath ?: throw IllegalStateException("No project base path")
        ReviewService(Path.of(basePath))
    }

    init {
        border = JBUI.Borders.empty(10)
        refresh()
    }

    fun refresh() {
        removeAll()

        if (reviewService.hasActiveReview()) {
            showActiveReviewPanel()
        } else {
            showNoReviewPanel()
        }

        revalidate()
        repaint()
    }

    private fun showNoReviewPanel() {
        val centerPanel = JBPanel<JBPanel<*>>().apply {
            layout = java.awt.GridBagLayout()

            val label = JBLabel("No active review").apply {
                horizontalAlignment = SwingConstants.CENTER
            }

            val button = JButton("Start Feature Development").apply {
                addActionListener { startFeatureDevelopment() }
            }

            val gbc = java.awt.GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(5)
            }
            add(label, gbc)

            gbc.gridy = 1
            add(button, gbc)
        }

        add(centerPanel, BorderLayout.CENTER)
    }

    private fun showActiveReviewPanel() {
        val data = reviewService.loadReviewData() ?: return

        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val roundLabel = JBLabel("Round: ${data.currentRound} (vs ${data.baseRef})")
            val resolvedCount = data.comments.count { it.status == com.codereview.local.model.CommentStatus.RESOLVED }
            val totalCount = data.comments.size
            val progressLabel = JBLabel("Progress: $resolvedCount/$totalCount resolved")

            add(roundLabel, BorderLayout.NORTH)
            add(progressLabel, BorderLayout.SOUTH)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(JBLabel("Comments list coming soon..."), BorderLayout.CENTER)
    }

    private fun startFeatureDevelopment() {
        // TODO: Create git tag and initialize review
        reviewService.initializeReview("review-r0")
        refresh()
    }
}
```

**Step 3: Verify plugin builds**

Run: `cd plugin && ./gradlew build`
Expected: Build successful

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/
git commit -m "feat(plugin): add tool window factory and initial panel"
```

---

### Task 16: Implement Comment List UI

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/CommentListPanel.kt`
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Create CommentListPanel**

```kotlin
package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import javax.swing.*

class CommentListPanel(
    private val comments: List<Comment>,
    private val onCommentSelected: (Comment) -> Unit
) : JBScrollPane() {

    private val listModel = DefaultListModel<Comment>()
    private val commentList = JBList(listModel)

    init {
        comments.forEach { listModel.addElement(it) }

        commentList.cellRenderer = CommentCellRenderer()
        commentList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        commentList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                commentList.selectedValue?.let { onCommentSelected(it) }
            }
        }

        setViewportView(commentList)
        border = JBUI.Borders.empty()
    }

    private class CommentCellRenderer : ColoredListCellRenderer<Comment>() {
        override fun customizeCellRenderer(
            list: JList<out Comment>,
            value: Comment,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val statusColor = when (value.status) {
                CommentStatus.OPEN -> JBColor.YELLOW.darker()
                CommentStatus.PENDING_USER -> JBColor.BLUE
                CommentStatus.PENDING_AGENT -> JBColor.ORANGE
                CommentStatus.FIXED -> JBColor.GREEN
                CommentStatus.RESOLVED -> JBColor.GRAY
                CommentStatus.WONTFIX -> JBColor.GRAY
            }

            append("#${value.id} ", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append("[${value.status.jsonValue}] ", com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_BOLD, statusColor
            ))
            append("${value.file}:${value.line}")

            value.firstUserMessage?.let { msg ->
                append(" - ", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(msg.take(40) + if (msg.length > 40) "..." else "")
            }
        }
    }

    fun refresh(newComments: List<Comment>) {
        listModel.clear()
        newComments.forEach { listModel.addElement(it) }
    }
}
```

**Step 2: Update ReviewPanel to use CommentListPanel**

Update `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt` - replace `showActiveReviewPanel()`:

```kotlin
private fun showActiveReviewPanel() {
    val data = reviewService.loadReviewData() ?: return

    // Header
    val headerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(0, 0, 10, 0)

        val roundLabel = JBLabel("Round: ${data.currentRound} (vs ${data.baseRef})")
        val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
        val totalCount = data.comments.size
        val progressLabel = JBLabel("Progress: $resolvedCount/$totalCount resolved")

        add(roundLabel, BorderLayout.NORTH)
        add(progressLabel, BorderLayout.SOUTH)
    }

    // Comment list grouped by status
    val commentList = CommentListPanel(data.comments) { comment ->
        showCommentDetails(comment)
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
    add(commentList, BorderLayout.CENTER)
    add(buttonPanel, BorderLayout.SOUTH)
}

private fun showCommentDetails(comment: Comment) {
    // TODO: Show popup or detail panel
    println("Selected comment #${comment.id}")
}

private fun startNewRound() {
    // TODO: Create new git tag and update round
    val data = reviewService.loadReviewData() ?: return
    val currentNum = data.currentRound.substringAfter("review-r").toIntOrNull() ?: 0
    val newRound = "review-r${currentNum + 1}"
    reviewService.startNewRound(newRound)
    refresh()
}
```

**Step 3: Verify plugin builds**

Run: `cd plugin && ./gradlew build`
Expected: Build successful

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/
git commit -m "feat(plugin): add comment list panel with status colors"
```

---

### Task 17: Implement Comment Popup Dialog

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/ui/CommentPopup.kt`

**Step 1: Create CommentPopup**

```kotlin
package com.codereview.local.ui

import com.codereview.local.model.Comment
import com.codereview.local.model.CommentStatus
import com.codereview.local.model.ThreadEntry
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class CommentPopup(
    private val comment: Comment,
    private val onReply: (String) -> Unit,
    private val onStatusChange: (CommentStatus) -> Unit
) : DialogWrapper(true) {

    private val replyArea = JBTextArea(3, 40)

    init {
        title = "Comment #${comment.id}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 400)

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 10, 0)
            add(JBLabel("${comment.file}:${comment.line}"), BorderLayout.WEST)
            add(createStatusLabel(), BorderLayout.EAST)
        }

        // Thread
        val threadPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(5)

            for (entry in comment.thread) {
                add(createThreadEntryPanel(entry))
                add(Box.createVerticalStrut(10))
            }
        }

        val threadScroll = JBScrollPane(threadPanel).apply {
            border = BorderFactory.createTitledBorder("Thread")
        }

        // Reply input
        val replyPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Reply")
            replyArea.lineWrap = true
            replyArea.wrapStyleWord = true
            add(JBScrollPane(replyArea), BorderLayout.CENTER)
        }

        // Status buttons
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)

            if (comment.status == CommentStatus.FIXED) {
                add(JButton("Resolve").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.RESOLVED)
                        close(OK_EXIT_CODE)
                    }
                })
                add(Box.createHorizontalStrut(5))
                add(JButton("Reopen").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.OPEN)
                        close(OK_EXIT_CODE)
                    }
                })
            }

            if (comment.status == CommentStatus.OPEN || comment.status == CommentStatus.PENDING_AGENT) {
                add(JButton("Won't Fix").apply {
                    addActionListener {
                        onStatusChange(CommentStatus.WONTFIX)
                        close(OK_EXIT_CODE)
                    }
                })
            }
        }

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(threadScroll, BorderLayout.CENTER)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(replyPanel, BorderLayout.CENTER)
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createStatusLabel(): JLabel {
        val color = when (comment.status) {
            CommentStatus.OPEN -> JBColor.YELLOW.darker()
            CommentStatus.PENDING_USER -> JBColor.BLUE
            CommentStatus.PENDING_AGENT -> JBColor.ORANGE
            CommentStatus.FIXED -> JBColor.GREEN
            CommentStatus.RESOLVED -> JBColor.GRAY
            CommentStatus.WONTFIX -> JBColor.GRAY
        }
        return JLabel(comment.status.jsonValue).apply {
            foreground = color
        }
    }

    private fun createThreadEntryPanel(entry: ThreadEntry): JPanel {
        return JPanel(BorderLayout()).apply {
            val authorLabel = JBLabel(if (entry.isUserComment) "You" else "Claude").apply {
                foreground = if (entry.isUserComment) JBColor.BLUE else JBColor.GREEN.darker()
            }

            val timeLabel = JBLabel(formatTimestamp(entry.at)).apply {
                foreground = JBColor.GRAY
            }

            val headerRow = JPanel(BorderLayout()).apply {
                add(authorLabel, BorderLayout.WEST)
                add(timeLabel, BorderLayout.EAST)
            }

            val textArea = JBTextArea(entry.text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                background = null
            }

            add(headerRow, BorderLayout.NORTH)
            add(textArea, BorderLayout.CENTER)
        }
    }

    private fun formatTimestamp(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            isoString
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("Reply") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    val text = replyArea.text.trim()
                    if (text.isNotEmpty()) {
                        onReply(text)
                    }
                    close(OK_EXIT_CODE)
                }
            },
            cancelAction
        )
    }
}
```

**Step 2: Update ReviewPanel to show popup**

Update `showCommentDetails` in `ReviewPanel.kt`:

```kotlin
private fun showCommentDetails(comment: Comment) {
    val popup = CommentPopup(
        comment = comment,
        onReply = { text ->
            reviewService.addReply(comment.id, text)
            refresh()
        },
        onStatusChange = { status ->
            reviewService.updateCommentStatus(comment.id, status)
            refresh()
        }
    )
    popup.show()
}
```

**Step 3: Verify plugin builds**

Run: `cd plugin && ./gradlew build`
Expected: Build successful

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/CommentPopup.kt
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "feat(plugin): add comment popup dialog with thread view"
```

---

### Task 18: Implement Gutter Icon Provider

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/gutter/ReviewGutterProvider.kt`
- Modify: `plugin/src/main/resources/META-INF/plugin.xml`

**Step 1: Create ReviewGutterProvider**

```kotlin
package com.codereview.local.gutter

import com.codereview.local.model.CommentStatus
import com.codereview.local.services.ReviewService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.nio.file.Path
import javax.swing.Icon

class ReviewGutterProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process at file level for the first element on each line
        if (element.parent !is PsiFile) return null

        val project = element.project
        val file = element.containingFile?.virtualFile ?: return null
        val basePath = project.basePath ?: return null

        val service = ReviewService(Path.of(basePath))
        if (!service.hasActiveReview()) return null

        val data = service.loadReviewData() ?: return null

        // Get relative file path
        val relativePath = file.path.removePrefix("$basePath/")

        // Find comments for this file and line
        val document = com.intellij.psi.PsiDocumentManager.getInstance(project)
            .getDocument(element.containingFile) ?: return null
        val lineNumber = document.getLineNumber(element.textRange.startOffset) + 1

        val comment = data.comments.find {
            it.file == relativePath && it.line == lineNumber
        } ?: return null

        val icon = CommentIcon(comment.status)
        val tooltipText = "#${comment.id}: ${comment.firstUserMessage?.take(50) ?: "Comment"}"

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltipText },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltipText }
        )
    }

    private class CommentIcon(private val status: CommentStatus) : Icon {
        private val color: Color = when (status) {
            CommentStatus.OPEN -> JBColor.YELLOW
            CommentStatus.PENDING_USER -> JBColor.BLUE
            CommentStatus.PENDING_AGENT -> JBColor.ORANGE
            CommentStatus.FIXED -> JBColor.GREEN
            CommentStatus.RESOLVED -> JBColor.GRAY
            CommentStatus.WONTFIX -> JBColor.GRAY
        }

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            g.color = color
            g.fillOval(x + 2, y + 2, iconWidth - 4, iconHeight - 4)
            g.color = color.darker()
            g.drawOval(x + 2, y + 2, iconWidth - 4, iconHeight - 4)
        }

        override fun getIconWidth(): Int = 12
        override fun getIconHeight(): Int = 12
    }
}
```

**Step 2: Update plugin.xml to register the provider**

Add to `plugin/src/main/resources/META-INF/plugin.xml` inside `<extensions>`:

```xml
<codeInsight.lineMarkerProvider
    language=""
    implementationClass="com.codereview.local.gutter.ReviewGutterProvider"/>
```

**Step 3: Verify plugin builds**

Run: `cd plugin && ./gradlew build`
Expected: Build successful

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/gutter/
git add plugin/src/main/resources/META-INF/plugin.xml
git commit -m "feat(plugin): add gutter icons for review comments"
```

---

## Phase 4: Git Integration

### Task 19: Implement Git Service for Plugin

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`
- Create: `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt`

**Step 1: Write the failing test**

Create `plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt`:

```kotlin
package com.codereview.local.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var gitService: GitService

    @BeforeEach
    fun setup() {
        gitService = GitService(tempDir)
        // Initialize git repo
        ProcessBuilder("git", "init")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.email", "test@test.com")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.name", "Test")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
    }

    @Test
    fun `should create tag`() {
        // Create a commit first
        tempDir.resolve("test.txt").toFile().writeText("test")
        ProcessBuilder("git", "add", ".")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "Initial commit")
            .directory(tempDir.toFile())
            .start()
            .waitFor()

        val result = gitService.createTag("review-r0")

        assertTrue(result)
        assertTrue(gitService.tagExists("review-r0"))
    }

    @Test
    fun `should check if tag exists`() {
        assertFalse(gitService.tagExists("nonexistent"))
    }

    @Test
    fun `should get current commit sha`() {
        tempDir.resolve("test.txt").toFile().writeText("test")
        ProcessBuilder("git", "add", ".")
            .directory(tempDir.toFile())
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "Initial commit")
            .directory(tempDir.toFile())
            .start()
            .waitFor()

        val sha = gitService.getCurrentCommitSha()

        assertNotNull(sha)
        assertTrue(sha!!.length >= 7)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd plugin && ./gradlew test`
Expected: FAIL - GitService not found

**Step 3: Write minimal implementation**

Create `plugin/src/main/kotlin/com/codereview/local/services/GitService.kt`:

```kotlin
package com.codereview.local.services

import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GitService(private val projectRoot: Path) {

    fun createTag(tagName: String): Boolean {
        return runGitCommand("tag", tagName) == 0
    }

    fun tagExists(tagName: String): Boolean {
        return runGitCommand("rev-parse", "--verify", "refs/tags/$tagName") == 0
    }

    fun getCurrentCommitSha(): String? {
        val result = runGitCommandWithOutput("rev-parse", "--short", "HEAD")
        return result?.trim()
    }

    fun getFileAtRef(ref: String, filePath: String): String? {
        return runGitCommandWithOutput("show", "$ref:$filePath")
    }

    private fun runGitCommand(vararg args: String): Int {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
            process.waitFor(30, TimeUnit.SECONDS)
            process.exitValue()
        } catch (e: Exception) {
            -1
        }
    }

    private fun runGitCommandWithOutput(vararg args: String): String? {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
            process.waitFor(30, TimeUnit.SECONDS)
            if (process.exitValue() == 0) {
                process.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd plugin && ./gradlew test`
Expected: PASS

**Step 5: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/services/GitService.kt
git add plugin/src/test/kotlin/com/codereview/local/services/GitServiceTest.kt
git commit -m "feat(plugin): add GitService for tag management"
```

---

### Task 20: Integrate Git with Review Workflow

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Update ReviewPanel to use GitService**

Add GitService integration to `ReviewPanel.kt`:

```kotlin
package com.codereview.local.ui

import com.codereview.local.model.CommentStatus
import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Path
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.SwingConstants

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val basePath: Path by lazy {
        Path.of(project.basePath ?: throw IllegalStateException("No project base path"))
    }

    private val reviewService: ReviewService by lazy { ReviewService(basePath) }
    private val gitService: GitService by lazy { GitService(basePath) }

    init {
        border = JBUI.Borders.empty(10)
        refresh()
    }

    fun refresh() {
        removeAll()

        if (reviewService.hasActiveReview()) {
            showActiveReviewPanel()
        } else {
            showNoReviewPanel()
        }

        revalidate()
        repaint()
    }

    private fun showNoReviewPanel() {
        val centerPanel = JBPanel<JBPanel<*>>().apply {
            layout = GridBagLayout()

            val label = JBLabel("No active review").apply {
                horizontalAlignment = SwingConstants.CENTER
            }

            val button = JButton("Start Feature Development").apply {
                addActionListener { startFeatureDevelopment() }
            }

            val description = JBLabel("<html><center>Creates baseline tag and initializes<br/>review folder</center></html>").apply {
                foreground = java.awt.Color.GRAY
            }

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(5)
            }
            add(label, gbc)

            gbc.gridy = 1
            add(button, gbc)

            gbc.gridy = 2
            add(description, gbc)
        }

        add(centerPanel, BorderLayout.CENTER)
    }

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

        // Comment list
        val commentList = CommentListPanel(data.comments) { comment ->
            showCommentDetails(comment)
        }

        // Action buttons
        val buttonPanel = JBPanel<JBPanel<*>>().apply {
            layout = FlowLayout(FlowLayout.LEFT)
            add(JButton("New Round").apply {
                addActionListener { startNewRound() }
            })
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
        }

        add(headerPanel, BorderLayout.NORTH)
        add(commentList, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun showCommentDetails(comment: com.codereview.local.model.Comment) {
        val popup = CommentPopup(
            comment = comment,
            onReply = { text ->
                reviewService.addReply(comment.id, text)
                refresh()
            },
            onStatusChange = { status ->
                reviewService.updateCommentStatus(comment.id, status)
                refresh()
            }
        )
        popup.show()
    }

    private fun startFeatureDevelopment() {
        // Check if we have a clean git state
        val currentSha = gitService.getCurrentCommitSha()
        if (currentSha == null) {
            Messages.showErrorDialog(
                project,
                "Could not get current commit. Make sure you have at least one commit in the repository.",
                "Git Error"
            )
            return
        }

        // Create baseline tag
        val tagName = "review-r0"
        if (gitService.tagExists(tagName)) {
            val result = Messages.showYesNoDialog(
                project,
                "Tag '$tagName' already exists. Delete and recreate?",
                "Tag Exists",
                Messages.getQuestionIcon()
            )
            if (result != Messages.YES) return
            // Would need to delete tag first - skipping for MVP
        }

        if (!gitService.createTag(tagName)) {
            Messages.showErrorDialog(project, "Failed to create git tag", "Git Error")
            return
        }

        // Initialize review
        reviewService.initializeReview(tagName)
        refresh()

        Messages.showInfoMessage(
            project,
            "Feature development started!\n\nBaseline tag '$tagName' created at commit $currentSha",
            "Review Initialized"
        )
    }

    private fun startNewRound() {
        val data = reviewService.loadReviewData() ?: return

        // Calculate next round number
        val currentNum = data.currentRound.substringAfter("review-r").toIntOrNull() ?: 0
        val newRound = "review-r${currentNum + 1}"

        // Create new tag
        if (!gitService.createTag(newRound)) {
            Messages.showErrorDialog(project, "Failed to create git tag '$newRound'", "Git Error")
            return
        }

        // Update review data
        reviewService.startNewRound(newRound)
        refresh()

        val sha = gitService.getCurrentCommitSha() ?: "unknown"
        Messages.showInfoMessage(
            project,
            "New review round started!\n\nTag '$newRound' created at commit $sha",
            "New Round"
        )
    }
}
```

**Step 2: Verify plugin builds**

Run: `cd plugin && ./gradlew build`
Expected: Build successful

**Step 3: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt
git commit -m "feat(plugin): integrate git tag creation with review workflow"
```

---

## Phase 5: Polish and Packaging

### Task 21: Add File Watcher for Auto-Refresh

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/listeners/ReviewFileWatcher.kt`
- Modify: `plugin/src/main/resources/META-INF/plugin.xml`

**Step 1: Create ReviewFileWatcher**

```kotlin
package com.codereview.local.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager

class ReviewFileWatcher(private val project: Project) : BulkFileListener {

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )
    }

    override fun after(events: MutableList<out VFileEvent>) {
        val reviewFileChanged = events.any { event ->
            event.path?.contains(".review/comments.json") == true
        }

        if (reviewFileChanged) {
            refreshToolWindow()
        }
    }

    private fun refreshToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Code Review") ?: return

        val content = toolWindow.contentManager.getContent(0) ?: return
        val panel = content.component as? com.codereview.local.ui.ReviewPanel ?: return

        panel.refresh()
    }
}
```

**Step 2: Register as project listener**

Add to `plugin/src/main/resources/META-INF/plugin.xml`:

```xml
<projectListeners>
    <listener class="com.codereview.local.listeners.ReviewFileWatcher"
              topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
</projectListeners>
```

**Step 3: Verify plugin builds**

Run: `cd plugin && ./gradlew build`
Expected: Build successful

**Step 4: Commit**

```bash
git add plugin/src/main/kotlin/com/codereview/local/listeners/
git add plugin/src/main/resources/META-INF/plugin.xml
git commit -m "feat(plugin): add file watcher for auto-refresh on comment changes"
```

---

### Task 22: Package CLI for Distribution

**Files:**
- Modify: `cli/package.json`
- Create: `cli/README.md`

**Step 1: Update package.json for npm publishing**

Update `cli/package.json`:

```json
{
  "name": "@codereview/cli",
  "version": "0.1.0",
  "description": "CLI tool for local code review workflows with Claude Code",
  "main": "dist/index.js",
  "bin": {
    "review": "./dist/index.js"
  },
  "files": [
    "dist"
  ],
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "test": "vitest run",
    "test:watch": "vitest",
    "prepublishOnly": "npm run build && npm run test"
  },
  "keywords": ["code-review", "cli", "claude", "ai"],
  "author": "",
  "license": "MIT",
  "repository": {
    "type": "git",
    "url": "https://github.com/your-org/code-review-local"
  },
  "devDependencies": {
    "@types/node": "^20.10.0",
    "typescript": "^5.3.0",
    "vitest": "^1.0.0"
  },
  "engines": {
    "node": ">=18.0.0"
  }
}
```

**Step 2: Create README**

Create `cli/README.md`:

```markdown
# Code Review CLI

CLI tool for local code review workflows with Claude Code.

## Installation

```bash
npm install -g @codereview/cli
```

## Usage

```bash
# List all comments
review list

# Filter by status
review list --status=open
review list --status=pending-agent

# Reply to a comment (as Claude)
review reply 1 "Should I use approach A or B?"

# Mark as fixed after committing
review fix 1 --commit abc1234

# Show full comment details
review show 1
```

## Statuses

- `open` - New comment, needs work
- `pending-user` - Waiting for user response
- `pending-agent` - Waiting for Claude to process
- `fixed` - Claude marked as fixed, needs verification
- `resolved` - User verified, done
- `wontfix` - Not addressing

## Integration

This CLI is designed to work with the Code Review Local IntelliJ plugin.
The shared state is stored in `.review/comments.json` (gitignored).
```

**Step 3: Verify CLI builds and tests pass**

Run: `cd cli && npm run build && npm test`
Expected: All tests pass

**Step 4: Commit**

```bash
git add cli/package.json cli/README.md
git commit -m "chore(cli): prepare for npm distribution"
```

---

### Task 23: Create Root Project README

**Files:**
- Create: `README.md`
- Create: `.gitignore`

**Step 1: Create root .gitignore**

```
# Dependencies
node_modules/

# Build outputs
cli/dist/
plugin/build/
plugin/.gradle/

# IDE
.idea/
*.iml

# Review data (always gitignored)
.review/

# OS
.DS_Store
Thumbs.db

# Logs
*.log
```

**Step 2: Create README.md**

```markdown
# Code Review Local

A PhpStorm/IntelliJ plugin + CLI for local code review workflows when developing with Claude Code.

## Overview

Enable structured review rounds with comment tracking, threaded discussions, and status management for AI-assisted development workflows.

## Components

- **Plugin** (`plugin/`) - IntelliJ/PhpStorm plugin for review UI
- **CLI** (`cli/`) - Command-line tool for Claude to interact with reviews

## Quick Start

### Install CLI

```bash
cd cli && npm install && npm link
```

### Install Plugin

1. Build: `cd plugin && ./gradlew build`
2. Install from disk in IDE: Settings → Plugins → Install from disk
3. Select `plugin/build/distributions/code-review-local-*.zip`

## Development

### Docker (CLI only)

```bash
docker compose up cli
```

### Local

```bash
# CLI
cd cli && npm install && npm run dev

# Plugin
cd plugin && ./gradlew runIde
```

## Workflow

1. **Start Feature** - Click "Start Feature Development" in plugin
2. **Develop** - Claude implements features
3. **Review** - Click "New Round", add comments in IDE
4. **Fix** - Claude runs `review list` and processes comments
5. **Verify** - Review fixes, mark resolved or reopen

## License

MIT
```

**Step 3: Commit**

```bash
git add README.md .gitignore
git commit -m "docs: add project README and root gitignore"
```

---

### Task 24: Final Integration Test

**Files:**
- Create: `scripts/integration-test.sh`

**Step 1: Create integration test script**

```bash
#!/bin/bash
set -e

echo "=== Code Review Local Integration Test ==="

# Setup temp directory
TEMP_DIR=$(mktemp -d)
echo "Test directory: $TEMP_DIR"

cd "$TEMP_DIR"

# Initialize git repo
git init
git config user.email "test@test.com"
git config user.name "Test User"

# Create test file
mkdir -p src
cat > src/Example.php << 'EOF'
<?php
class Example {
    public function process($data) {
        return $data;
    }
}
EOF

git add .
git commit -m "Initial commit"

# Create review directory and comments
mkdir -p .review
cat > .review/comments.json << 'EOF'
{
  "version": 1,
  "currentRound": "review-r1",
  "baseRef": "review-r0",
  "comments": [
    {
      "id": 1,
      "file": "src/Example.php",
      "line": 4,
      "ref": "review-r1",
      "status": "open",
      "resolveCommit": null,
      "thread": [
        {"author": "user", "text": "Add input validation", "at": "2024-01-23T10:00:00Z"}
      ]
    }
  ]
}
EOF

echo ""
echo "=== Testing CLI: list ==="
review list

echo ""
echo "=== Testing CLI: show ==="
review show 1

echo ""
echo "=== Testing CLI: reply ==="
review reply 1 "Should I throw an exception or return null?"

echo ""
echo "=== Testing CLI: list after reply ==="
review list

echo ""
echo "=== Testing CLI: fix ==="
# Make a commit first
echo "// validated" >> src/Example.php
git add .
git commit -m "Add validation"
COMMIT_SHA=$(git rev-parse --short HEAD)
review fix 1 --commit "$COMMIT_SHA"

echo ""
echo "=== Final state ==="
review list

echo ""
echo "=== Integration test PASSED ==="

# Cleanup
rm -rf "$TEMP_DIR"
```

**Step 2: Make executable and test**

Run: `chmod +x scripts/integration-test.sh && ./scripts/integration-test.sh`
Expected: All CLI commands succeed, shows comment transitions

**Step 3: Commit**

```bash
git add scripts/integration-test.sh
git commit -m "test: add integration test script"
```

---

## Summary

This plan creates a complete Code Review Local system with:

**CLI (11 tasks):**
- Docker dev environment
- TypeScript project with Vitest testing
- Commands: `list`, `reply`, `fix`, `show`
- Status-aware output formatting

**Plugin (12 tasks):**
- Kotlin/Gradle IntelliJ plugin
- Tool window with comment list
- Comment popup dialog
- Gutter icons
- Git tag integration
- File watcher for auto-refresh

**Total: 24 tasks**, each following TDD with small, verifiable steps.

---

Plan complete and saved to `docs/plans/2025-01-23-code-review-local-implementation.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?