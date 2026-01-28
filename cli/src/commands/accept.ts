import { execSync } from 'child_process';
import { ReviewStore } from '../store';

function getCurrentCommitSha(cwd: string): string {
  const sha = execSync('git rev-parse HEAD', { cwd, encoding: 'utf-8' }).trim();
  return sha;
}

export function acceptChanges(store: ReviewStore, cwd: string): string {
  const data = store.load();
  const currentHead = getCurrentCommitSha(cwd);

  if (data.baseCommit === currentHead) {
    return 'No new changes to accept. Baseline is already at current commit.';
  }

  const oldBase = data.baseCommit.slice(0, 7);
  const newBase = currentHead.slice(0, 7);

  data.baseCommit = currentHead;
  data.reviewedFiles = [];
  store.save(data);

  return `Accepted changes. Baseline moved from ${oldBase} to ${newBase}.`;
}
