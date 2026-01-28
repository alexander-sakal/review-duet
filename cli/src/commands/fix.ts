import { ReviewStore } from '../store';

export function markAsFixed(store: ReviewStore, id: number, commitSha: string, message?: string): void {
  store.updateComment(id, (comment) => {
    comment.status = 'fixed';
    comment.resolveCommit = commitSha;

    const text = message
      ? `${message}\n\nFixed in ${commitSha}`
      : `Fixed in ${commitSha}`;

    comment.thread.push({
      author: 'agent',
      text,
      at: new Date().toISOString()
    });
  });
}
