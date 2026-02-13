import { ReviewStore } from '../store';

export function acceptChanges(store: ReviewStore): string {
  const data = store.load();

  if (!data.reviewedFiles?.length) {
    return 'No reviewed files to clear.';
  }

  data.reviewedFiles = [];
  store.save(data);

  return 'Accepted changes. Cleared reviewed files list.';
}
