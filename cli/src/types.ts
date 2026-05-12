export type CommentStatus =
  | 'open'
  | 'fixed'
  | 'resolved';

const VALID_STATUSES: CommentStatus[] = [
  'open',
  'fixed',
  'resolved'
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
  mode?: string;
  comments: Comment[];
  reviewedFiles?: string[];
}

export type FileCommentStatus = 'open' | 'fixed' | 'outdated';

export interface FileComment {
  id: number;
  line: number;
  lineText: string;
  contextBefore: string[];
  contextAfter: string[];
  text: string;
  status: FileCommentStatus;
  createdAt: string;
}

export interface FileReviewData {
  version: number;
  mode: 'file';
  filePath: string;
  fileHashAtStart: string;
  lastSeenHash: string;
  createdAt: string;
  comments: FileComment[];
}

export type AnyReviewData = ReviewData | FileReviewData;

export function isFileReviewData(data: AnyReviewData): data is FileReviewData {
  return (data as FileReviewData).mode === 'file';
}
