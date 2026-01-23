import { ReviewStore } from '../store';

export function replyToComment(store: ReviewStore, id: number, message: string): void {
  store.updateComment(id, (comment) => {
    comment.thread.push({
      author: 'agent',
      text: message,
      at: new Date().toISOString()
    });

    // Agent reply sets status to pending-user (waiting for user response)
    if (comment.status === 'open' || comment.status === 'pending-agent') {
      comment.status = 'pending-user';
    }
  });
}
