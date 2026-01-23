import { ReviewStore } from '../store';

export function markAsFixed(store: ReviewStore, id: number, commitSha: string): void {
  store.updateComment(id, (comment) => {
    comment.status = 'fixed';
    comment.resolveCommit = commitSha;

    comment.thread.push({
      author: 'agent',
      text: `Fixed in ${commitSha}`,
      at: new Date().toISOString()
    });
  });
}
