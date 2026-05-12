import { ReviewStore } from '../store';
import { isFileReviewData, FileReviewData } from '../types';

export function showComment(store: ReviewStore, id: number): string {
  const data = store.load();

  if (isFileReviewData(data)) {
    const comment = (data as FileReviewData).comments.find(c => c.id === id);
    if (!comment) {
      throw new Error(`Comment #${id} not found`);
    }
    const lines: string[] = [];
    lines.push(`Comment #${comment.id}`);
    lines.push(`File: ${data.filePath}:${comment.line}`);
    lines.push(`Status: ${comment.status}`);
    lines.push('');
    lines.push(comment.text);
    return lines.join('\n');
  }

  const comment = store.getComment(id);

  if (!comment) {
    throw new Error(`Comment #${id} not found`);
  }

  const lines: string[] = [];

  lines.push(`Comment #${comment.id}`);
  lines.push(`File: ${comment.file}:${comment.line}`);
  lines.push(`Status: ${comment.status}`);
  lines.push(`Commit: ${comment.commit}`);

  if (comment.resolveCommit) {
    lines.push(`Resolve Commit: ${comment.resolveCommit}`);
  }

  lines.push('');
  lines.push('Thread:');
  lines.push('─'.repeat(40));

  for (const entry of comment.thread) {
    const authorLabel = entry.author === 'user' ? 'User' : 'Claude';
    const timestamp = new Date(entry.at).toLocaleString();
    lines.push(`[${authorLabel}] ${timestamp}`);
    lines.push(entry.text);
    lines.push('');
  }

  lines.push('─'.repeat(40));
  lines.push(`View original: git show ${comment.commit}:${comment.file}`);

  return lines.join('\n');
}
