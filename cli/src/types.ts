export type CommentStatus =
  | 'open'
  | 'pending-user'
  | 'pending-agent'
  | 'fixed'
  | 'resolved'
  | 'wontfix';

const VALID_STATUSES: CommentStatus[] = [
  'open',
  'pending-user',
  'pending-agent',
  'fixed',
  'resolved',
  'wontfix'
];

export function isValidStatus(status: string): status is CommentStatus {
  return VALID_STATUSES.includes(status as CommentStatus);
}

export type Author = 'user' | 'agent';

export interface ThreadEntry {
  author: Author;
  text: string;
  at: string; // ISO 8601 timestamp
}

export interface Comment {
  id: number;
  file: string;
  line: number;
  commit: string;
  status: CommentStatus;
  resolveCommit: string | null;
  thread: ThreadEntry[];
}

export interface ReviewData {
  version: number;
  baseCommit: string;
  comments: Comment[];
}
