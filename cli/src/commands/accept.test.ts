import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { acceptChanges } from './accept';
import { ReviewStore } from '../store';
import { ReviewData, isFileReviewData } from '../types';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('acceptChanges', () => {
  let testDir: string;
  let reviewPath: string;
  let store: ReviewStore;

  beforeEach(() => {
    testDir = fs.mkdtempSync(path.join(os.tmpdir(), 'review-test-'));
    reviewPath = path.join(testDir, '.review-duet', 'main.json');
    store = new ReviewStore(reviewPath);
  });

  afterEach(() => {
    fs.rmSync(testDir, { recursive: true, force: true });
  });

  it('clears reviewedFiles without moving baseCommit', () => {
    const reviewDir = path.join(testDir, '.review-duet');
    fs.mkdirSync(reviewDir, { recursive: true });
    fs.writeFileSync(path.join(reviewDir, 'main.json'), JSON.stringify({
      version: 1,
      baseCommit: 'original-base-sha',
      comments: [],
      reviewedFiles: ['file1.ts', 'file2.ts']
    }));

    const result = acceptChanges(store);

    const data = store.load();
    if (isFileReviewData(data)) throw new Error('expected ReviewData');
    expect((data as ReviewData).baseCommit).toBe('original-base-sha');
    expect((data as ReviewData).reviewedFiles).toEqual([]);
    expect(result).toContain('Accepted changes');
  });

  it('returns message when no reviewed files to clear', () => {
    const reviewDir = path.join(testDir, '.review-duet');
    fs.mkdirSync(reviewDir, { recursive: true });
    fs.writeFileSync(path.join(reviewDir, 'main.json'), JSON.stringify({
      version: 1,
      baseCommit: 'some-sha',
      comments: []
    }));

    const result = acceptChanges(store);

    expect(result).toContain('No reviewed files to clear');
  });
});
