import * as fs from 'fs';
import * as path from 'path';
import { ReviewData, Comment, FileReviewData, FileComment, AnyReviewData, isFileReviewData } from './types';

export class ReviewStore {
  private readonly reviewPath: string;
  private data: AnyReviewData | null = null;

  constructor(reviewPath: string) {
    this.reviewPath = reviewPath;
  }

  load(): AnyReviewData {
    if (!fs.existsSync(this.reviewPath)) {
      throw new Error('No active review. Initialize with the IDE plugin first.');
    }

    const content = fs.readFileSync(this.reviewPath, 'utf-8');
    this.data = JSON.parse(content) as AnyReviewData;
    return this.data;
  }

  save(data: AnyReviewData): void {
    const dir = path.dirname(this.reviewPath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(this.reviewPath, JSON.stringify(data, null, 2));
    this.data = data;
  }

  getComment(id: number): Comment | undefined {
    if (!this.data) {
      this.load();
    }
    if (isFileReviewData(this.data!)) {
      return undefined;
    }
    return (this.data as ReviewData).comments.find(c => c.id === id);
  }

  updateComment(id: number, updater: (comment: Comment) => void): void {
    if (!this.data) {
      this.load();
    }
    if (isFileReviewData(this.data!)) {
      throw new Error('Use updateFileComment for file-mode reviews');
    }
    const comment = (this.data as ReviewData).comments.find(c => c.id === id);
    if (!comment) {
      throw new Error(`Comment #${id} not found`);
    }
    updater(comment);
    this.save(this.data!);
  }

  updateFileComment(id: number, updater: (comment: FileComment) => void): void {
    if (!this.data) {
      this.load();
    }
    if (!isFileReviewData(this.data!)) {
      throw new Error('Use updateComment for branch-mode reviews');
    }
    const comment = (this.data as FileReviewData).comments.find(c => c.id === id);
    if (!comment) {
      throw new Error(`Comment #${id} not found`);
    }
    updater(comment);
    this.save(this.data!);
  }
}
