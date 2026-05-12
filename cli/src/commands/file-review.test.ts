import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, writeFileSync, readFileSync, mkdirSync, rmSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { ReviewStore } from '../store';
import { listComments } from './list';
import { markFileCommentAsFixed } from './fix';
import { runCli } from '../index';

const SAMPLE_FILE_REVIEW = {
  version: 1,
  mode: 'file',
  filePath: 'docs/foo.md',
  fileHashAtStart: 'abc',
  lastSeenHash: 'abc',
  createdAt: '2026-05-12T10:00:00Z',
  comments: [
    {
      id: 1,
      line: 10,
      lineText: 'header',
      contextBefore: [],
      contextAfter: [],
      text: 'needs detail',
      status: 'open',
      createdAt: '2026-05-12T10:01:00Z',
    },
    {
      id: 2,
      line: 20,
      lineText: 'footer',
      contextBefore: [],
      contextAfter: [],
      text: 'remove this section',
      status: 'fixed',
      createdAt: '2026-05-12T10:02:00Z',
    },
  ],
};

describe('file-mode review JSON', () => {
  let tempDir: string;
  let reviewPath: string;

  beforeEach(() => {
    tempDir = mkdtempSync(join(tmpdir(), 'rd-file-review-'));
    reviewPath = join(tempDir, 'file-review.json');
    writeFileSync(reviewPath, JSON.stringify(SAMPLE_FILE_REVIEW));
  });

  afterEach(() => {
    rmSync(tempDir, { recursive: true, force: true });
  });

  describe('listComments', () => {
    it('shows all comments including file path and line', () => {
      const store = new ReviewStore(reviewPath);
      const out = listComments(store);
      expect(out).toContain('#1');
      expect(out).toContain('docs/foo.md:10');
      expect(out).toContain('needs detail');
      expect(out).toContain('#2');
      expect(out).toContain('docs/foo.md:20');
    });

    it('filters by status=open', () => {
      const store = new ReviewStore(reviewPath);
      const out = listComments(store, { status: 'open' });
      expect(out).toContain('#1');
      expect(out).not.toContain('#2');
    });

    it('filters by status=fixed', () => {
      const store = new ReviewStore(reviewPath);
      const out = listComments(store, { status: 'fixed' });
      expect(out).not.toContain('#1');
      expect(out).toContain('#2');
    });
  });

  describe('markFileCommentAsFixed', () => {
    it('marks a comment as fixed without requiring commitSha', () => {
      const store = new ReviewStore(reviewPath);
      markFileCommentAsFixed(store, 1);
      const updated = JSON.parse(readFileSync(reviewPath, 'utf-8'));
      expect(updated.comments[0].status).toBe('fixed');
    });

    it('persists other comments unchanged', () => {
      const store = new ReviewStore(reviewPath);
      markFileCommentAsFixed(store, 1);
      const updated = JSON.parse(readFileSync(reviewPath, 'utf-8'));
      expect(updated.comments[1].status).toBe('fixed');
      expect(updated.comments[1].id).toBe(2);
    });

    it('throws when comment id not found', () => {
      const store = new ReviewStore(reviewPath);
      expect(() => markFileCommentAsFixed(store, 999)).toThrow('Comment #999 not found');
    });
  });

  describe('runCli fix command (file-mode, no --commit)', () => {
    it('marks comment as fixed via CLI without --commit', () => {
      const out = runCli(['fix', '1', `--review=${reviewPath}`], tempDir);
      expect(out).toContain('#1');
      expect(out).toContain('fixed');
      const updated = JSON.parse(readFileSync(reviewPath, 'utf-8'));
      expect(updated.comments[0].status).toBe('fixed');
    });

    it('returns error message when id is not found', () => {
      expect(() => runCli(['fix', '999', `--review=${reviewPath}`], tempDir)).toThrow('Comment #999 not found');
    });
  });

  describe('runCli list command (file-mode)', () => {
    it('lists open comments via CLI', () => {
      const out = runCli(['list', '--status=open', `--review=${reviewPath}`], tempDir);
      expect(out).toContain('#1');
      expect(out).toContain('docs/foo.md:10');
      expect(out).not.toContain('#2');
    });
  });
});
