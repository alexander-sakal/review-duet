import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { acceptChanges } from './accept';
import { ReviewStore } from '../store';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { execSync } from 'child_process';

describe('acceptChanges', () => {
  let testDir: string;
  let store: ReviewStore;

  beforeEach(() => {
    testDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));

    // Initialize git repo
    execSync('git init', { cwd: testDir });
    execSync('git config user.email "test@test.com"', { cwd: testDir });
    execSync('git config user.name "Test"', { cwd: testDir });

    // Create initial commit
    fs.writeFileSync(path.join(testDir, 'file.txt'), 'initial');
    execSync('git add .', { cwd: testDir });
    execSync('git commit -m "initial"', { cwd: testDir });

    store = new ReviewStore(testDir);
  });

  afterEach(() => {
    fs.rmSync(testDir, { recursive: true, force: true });
  });

  it('moves baseCommit to current HEAD', () => {
    const initialCommit = execSync('git rev-parse HEAD', { cwd: testDir, encoding: 'utf-8' }).trim();

    // Create review with old base
    const reviewDir = path.join(testDir, '.review');
    fs.mkdirSync(reviewDir);
    fs.writeFileSync(path.join(reviewDir, 'comments.json'), JSON.stringify({
      version: 1,
      baseCommit: 'old-commit-sha',
      comments: [],
      reviewedFiles: ['file1.ts', 'file2.ts']
    }));

    const result = acceptChanges(store, testDir);

    const data = store.load();
    expect(data.baseCommit).toBe(initialCommit);
    expect(data.reviewedFiles).toEqual([]);
    expect(result).toContain('Accepted changes');
  });

  it('returns message when already at HEAD', () => {
    const currentHead = execSync('git rev-parse HEAD', { cwd: testDir, encoding: 'utf-8' }).trim();

    const reviewDir = path.join(testDir, '.review');
    fs.mkdirSync(reviewDir);
    fs.writeFileSync(path.join(reviewDir, 'comments.json'), JSON.stringify({
      version: 1,
      baseCommit: currentHead,
      comments: []
    }));

    const result = acceptChanges(store, testDir);

    expect(result).toContain('No new changes to accept');
  });
});
