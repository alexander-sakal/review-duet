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
    reviewPath = path.join(tempDir, '.review-duet', 'main.json');
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
      baseCommit: 'abc1234',
      comments: [
        { id: 1, file: 'a.ts', line: 1, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(reviewPath);
    markAsFixed(store, 1, 'abc1234');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].status).toBe('fixed');
    expect(updated.comments[0].resolveCommit).toBe('abc1234');
  });

  it('should add a thread entry noting the fix', () => {
    const data = {
      version: 1,
      baseCommit: 'abc1234',
      comments: [
        { id: 1, file: 'a.ts', line: 1, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(reviewPath);
    markAsFixed(store, 1, 'abc1234');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].thread).toHaveLength(2);
    expect(updated.comments[0].thread[1].text).toContain('abc1234');
  });

  it('should throw if comment not found', () => {
    const data = {
      version: 1,
      baseCommit: 'abc1234',
      comments: []
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(reviewPath);
    expect(() => markAsFixed(store, 999, 'abc')).toThrow('Comment #999 not found');
  });

  it('should include optional message in thread entry', () => {
    const data = {
      version: 1,
      baseCommit: 'abc1234',
      comments: [
        { id: 1, file: 'a.ts', line: 1, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
      ]
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(reviewPath);
    markAsFixed(store, 1, 'abc1234', 'Refactored the function to handle edge cases');

    const updated = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
    expect(updated.comments[0].thread[1].text).toContain('Refactored the function to handle edge cases');
    expect(updated.comments[0].thread[1].text).toContain('abc1234');
  });
});
