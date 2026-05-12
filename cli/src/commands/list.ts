import { ReviewStore } from '../store';
import { Comment, CommentStatus, FileComment, isFileReviewData } from '../types';

export interface ListOptions {
  status?: CommentStatus;
}

export function formatComment(comment: Comment): string {
  const lines: string[] = [];

  const statusTag = `[${comment.status}]`;
  const location = `${comment.file}:${comment.line}`;
  const commitRef = `@ ${comment.commit}`;

  lines.push(`#${comment.id} ${statusTag} ${location} ${commitRef}`);

  // Show first user message
  const firstUserMsg = comment.thread.find(t => t.author === 'user');
  if (firstUserMsg) {
    lines.push(`   "${firstUserMsg.text}"`);
  }

  // For fixed, show the commit
  if (comment.status === 'fixed' && comment.resolveCommit) {
    lines.push(`   └─ Fixed in ${comment.resolveCommit}`);
  }

  return lines.join('\n');
}

export function formatFileComment(comment: FileComment, filePath: string): string {
  const lines: string[] = [];

  const statusTag = `[${comment.status}]`;
  const location = `${filePath}:${comment.line}`;

  lines.push(`#${comment.id} ${statusTag} ${location}`);
  lines.push(`   "${comment.text}"`);

  return lines.join('\n');
}

export function listComments(store: ReviewStore, options: ListOptions = {}): string {
  const data = store.load();

  if (isFileReviewData(data)) {
    let comments = data.comments;
    if (options.status) {
      comments = comments.filter(c => c.status === options.status);
    }
    if (comments.length === 0) {
      return 'No comments found.';
    }
    return comments.map(c => formatFileComment(c, data.filePath)).join('\n\n');
  }

  let comments = data.comments;

  if (options.status) {
    comments = comments.filter(c => c.status === options.status);
  }

  if (comments.length === 0) {
    return 'No comments found.';
  }

  return comments.map(formatComment).join('\n\n');
}
