#!/usr/bin/env node

import { ReviewStore } from './store';
import { listComments } from './commands/list';
import { markAsFixed } from './commands/fix';
import { showComment } from './commands/show';
import { acceptChanges } from './commands/accept';
import { CommentStatus, isValidStatus } from './types';

export interface ParsedArgs {
  command: string;
  args: string[];
  options: Record<string, string>;
}

export function parseArgs(argv: string[]): ParsedArgs {
  const command = argv[0] || 'help';
  const args: string[] = [];
  const options: Record<string, string> = {};

  for (let i = 1; i < argv.length; i++) {
    const arg = argv[i];

    if (arg.startsWith('--')) {
      const [key, value] = arg.slice(2).split('=');
      if (value !== undefined) {
        options[key] = value;
      } else if (argv[i + 1] && !argv[i + 1].startsWith('--')) {
        options[key] = argv[++i];
      } else {
        options[key] = 'true';
      }
    } else {
      args.push(arg);
    }
  }

  return { command, args, options };
}

function getReviewPath(options: Record<string, string>, cwd: string): string {
  if (options.review) {
    return options.review;
  }
  // Default: .review-duet/{branch}.json
  const { execSync } = require('child_process');
  const branch = execSync('git branch --show-current', { cwd, encoding: 'utf-8' }).trim();
  return `${cwd}/.review-duet/${branch}.json`;
}

/**
 * Extract the repo root from a review file path.
 * e.g., /code/myproject/.review-duet/main.json -> /code/myproject
 */
function getRepoRootFromReviewPath(reviewPath: string): string {
  // Review path format: <repo-root>/.review-duet/<branch>.json
  const reviewDuetIndex = reviewPath.lastIndexOf('/.review-duet/');
  if (reviewDuetIndex !== -1) {
    return reviewPath.substring(0, reviewDuetIndex);
  }
  // Fallback: use directory of the file
  const path = require('path');
  return path.dirname(path.dirname(reviewPath));
}

export function runCli(argv: string[], cwd: string = process.cwd()): string {
  const { command, args, options } = parseArgs(argv);
  const reviewPath = getReviewPath(options, cwd);
  const store = new ReviewStore(reviewPath);

  switch (command) {
    case 'list': {
      const statusFilter = options.status as CommentStatus | undefined;
      if (statusFilter && !isValidStatus(statusFilter)) {
        throw new Error(`Invalid status: ${statusFilter}`);
      }
      return listComments(store, { status: statusFilter });
    }

    case 'fix': {
      const id = parseInt(args[0], 10);
      const commit = options.commit;
      const message = options.message;
      if (isNaN(id)) throw new Error('Invalid comment ID');
      if (!commit) throw new Error('--commit required');
      markAsFixed(store, id, commit, message);
      return `Marked comment #${id} as fixed (${commit})`;
    }

    case 'show': {
      const id = parseInt(args[0], 10);
      if (isNaN(id)) throw new Error('Invalid comment ID');
      return showComment(store, id);
    }

    case 'accept': {
      const repoRoot = getRepoRootFromReviewPath(reviewPath);
      return acceptChanges(store, repoRoot);
    }

    case 'help':
    default:
      return `review-duet CLI - Code review helper for Claude Code

Usage:
  review-duet list [--status=<status>]        List comments
  review-duet fix <id> --commit <sha> [--message "<text>"]  Mark as fixed
  review-duet show <id>                       Show comment details
  review-duet accept                          Accept changes, move baseline to HEAD

Global Options:
  --review=<path>  Explicit path to review file (default: .review-duet/{branch}.json)
                   All commands support this flag.

Statuses: open, fixed, resolved`;
  }
}

// Main entry point
if (require.main === module) {
  try {
    const output = runCli(process.argv.slice(2));
    console.log(output);
  } catch (error) {
    console.error(`Error: ${(error as Error).message}`);
    process.exit(1);
  }
}
