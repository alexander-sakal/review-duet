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
