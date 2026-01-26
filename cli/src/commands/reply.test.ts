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
      baseCommit: 'abc1234',
      comments: [
        { id: 1, file: 'a.ts', line: 1, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
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
      baseCommit: 'abc1234',
      comments: [
        { id: 1, file: 'a.ts', line: 1, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [{ author: 'user', text: 'Fix this', at: '2024-01-23T10:00:00Z' }] }
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
      baseCommit: 'abc1234',
      comments: []
    };
    fs.writeFileSync(reviewPath, JSON.stringify(data));

    const store = new ReviewStore(tempDir);
    expect(() => replyToComment(store, 999, 'Hello')).toThrow('Comment #999 not found');
  });
});
