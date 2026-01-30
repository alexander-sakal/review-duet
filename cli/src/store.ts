import * as fs from 'fs';
import * as path from 'path';
import { ReviewData, Comment } from './types';

export class ReviewStore {
  private readonly reviewPath: string;
  private data: ReviewData | null = null;

  constructor(reviewPath: string) {
    this.reviewPath = reviewPath;
  }

  load(): ReviewData {
    if (!fs.existsSync(this.reviewPath)) {
      throw new Error('No active review. Initialize with the IDE plugin first.');
    }

    const content = fs.readFileSync(this.reviewPath, 'utf-8');
    this.data = JSON.parse(content) as ReviewData;
    return this.data;
  }

  save(data: ReviewData): void {
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
    return this.data?.comments.find(c => c.id === id);
  }

  updateComment(id: number, updater: (comment: Comment) => void): void {
    if (!this.data) {
      this.load();
    }
    const comment = this.data?.comments.find(c => c.id === id);
    if (!comment) {
      throw new Error(`Comment #${id} not found`);
    }
    updater(comment);
    this.save(this.data!);
  }
}
