import { ReviewStore } from '../store';
import { Comment, CommentStatus } from '../types';

export interface ListOptions {
  status?: CommentStatus;
}

export function formatComment(comment: Comment): string {
  const lines: string[] = [];

  const statusTag = `[${comment.status}]`;
  const location = `${comment.file}:${comment.line}`;

  lines.push(`#${comment.id} ${statusTag} ${location}`);

  // Show first user message
  const firstUserMsg = comment.thread.find(t => t.author === 'user');
  if (firstUserMsg) {
    lines.push(`   "${firstUserMsg.text}"`);
  }

  // For pending-user, show the last agent message
  if (comment.status === 'pending-user') {
    const lastAgentMsg = [...comment.thread].reverse().find(t => t.author === 'agent');
    if (lastAgentMsg) {
      lines.push(`   └─ Claude asked: "${lastAgentMsg.text}"`);
    }
  }

  // For pending-agent, show the last user reply
  if (comment.status === 'pending-agent') {
    const lastUserMsg = [...comment.thread].reverse().find(t => t.author === 'user');
    if (lastUserMsg && comment.thread.length > 1) {
      lines.push(`   └─ You answered: "${lastUserMsg.text}"`);
    }
  }

  // For fixed, show the commit
  if (comment.status === 'fixed' && comment.resolveCommit) {
    lines.push(`   └─ Fixed in ${comment.resolveCommit}`);
  }

  return lines.join('\n');
}

export function listComments(store: ReviewStore, options: ListOptions = {}): string {
  const data = store.load();

  let comments = data.comments;

  if (options.status) {
    comments = comments.filter(c => c.status === options.status);
  }

  if (comments.length === 0) {
    return 'No comments found.';
  }

  return comments.map(formatComment).join('\n\n');
}
