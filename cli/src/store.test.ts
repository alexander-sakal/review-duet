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
    reviewPath = path.join(tempDir, '.review-duet', 'main.json');
  });

  afterEach(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  describe('load', () => {
    it('should load existing review data', () => {
      const data = {
        version: 1,
        baseCommit: 'abc1234',
        comments: []
      };

      fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(reviewPath);
      const loaded = store.load();

      expect(loaded.version).toBe(1);
      expect(loaded.baseCommit).toBe('abc1234');
    });

    it('should throw if no review file exists', () => {
      const store = new ReviewStore(reviewPath);
      expect(() => store.load()).toThrow('No active review');
    });
  });

  describe('save', () => {
    it('should save review data to file', () => {
      const store = new ReviewStore(reviewPath);
      const data = {
        version: 1,
        baseCommit: 'abc1234',
        comments: []
      };

      store.save(data);

      const saved = JSON.parse(fs.readFileSync(reviewPath, 'utf-8'));
      expect(saved.baseCommit).toBe('abc1234');
    });
  });

  describe('getComment', () => {
    it('should return comment by id', () => {
      const data = {
        version: 1,
        baseCommit: 'abc1234',
        comments: [
          { id: 1, file: 'test.ts', line: 10, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [] },
          { id: 2, file: 'test.ts', line: 20, commit: 'abc1234', status: 'open', resolveCommit: null, thread: [] }
        ]
      };

      fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(reviewPath);
      const comment = store.getComment(2);

      expect(comment?.line).toBe(20);
    });

    it('should return undefined for non-existent id', () => {
      const data = {
        version: 1,
        baseCommit: 'abc1234',
        comments: []
      };

      fs.mkdirSync(path.dirname(reviewPath), { recursive: true });
      fs.writeFileSync(reviewPath, JSON.stringify(data));

      const store = new ReviewStore(reviewPath);
      expect(store.getComment(999)).toBeUndefined();
    });
  });
});
