import { ReviewStore } from '../store';
import { isFileReviewData, ReviewData } from '../types';

export function acceptChanges(store: ReviewStore): string {
  const data = store.load();

  if (isFileReviewData(data)) {
    return 'No reviewed files to clear.';
  }

  const reviewData = data as ReviewData;
  if (!reviewData.reviewedFiles?.length) {
    return 'No reviewed files to clear.';
  }

  reviewData.reviewedFiles = [];
  store.save(reviewData);

  return 'Accepted changes. Cleared reviewed files list.';
}
