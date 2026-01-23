import { ReviewStore } from '../store';

export function showComment(store: ReviewStore, id: number): string {
  const comment = store.getComment(id);

  if (!comment) {
    throw new Error(`Comment #${id} not found`);
  }

  const lines: string[] = [];

  lines.push(`Comment #${comment.id}`);
  lines.push(`File: ${comment.file}:${comment.line}`);
  lines.push(`Status: ${comment.status}`);
  lines.push(`Ref: ${comment.ref}`);

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
  lines.push(`View original: git show ${comment.ref}:${comment.file}`);

  return lines.join('\n');
}
