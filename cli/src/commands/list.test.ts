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
