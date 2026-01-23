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
