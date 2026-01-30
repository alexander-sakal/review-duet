import { describe, it, expect } from 'vitest';
import { CommentStatus, isValidStatus, ThreadEntry, Comment, ReviewData } from './types';

describe('CommentStatus', () => {
  it('should have all valid statuses', () => {
    const statuses: CommentStatus[] = [
      'open',
      'fixed',
      'resolved'
    ];

    statuses.forEach(status => {
      expect(isValidStatus(status)).toBe(true);
    });
  });

  it('should reject invalid statuses', () => {
    expect(isValidStatus('invalid')).toBe(false);
    expect(isValidStatus('')).toBe(false);
  });
});

describe('ThreadEntry', () => {
  it('should validate author as user or agent', () => {
    const entry: ThreadEntry = {
      author: 'user',
      text: 'Test comment',
      at: '2024-01-23T10:00:00Z'
    };
    expect(entry.author).toBe('user');
  });
});
