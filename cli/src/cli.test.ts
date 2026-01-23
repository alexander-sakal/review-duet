import { describe, it, expect, beforeEach, afterEach } from 'vitest';
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
