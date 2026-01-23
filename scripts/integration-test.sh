#!/bin/bash
set -e

echo "=== Code Review Local Integration Test ==="

# Setup temp directory
TEMP_DIR=$(mktemp -d)
echo "Test directory: $TEMP_DIR"

cd "$TEMP_DIR"

# Initialize git repo
git init
git config user.email "test@test.com"
git config user.name "Test User"

# Create test file
mkdir -p src
cat > src/Example.php << 'EOF'
<?php
class Example {
    public function process($data) {
        return $data;
    }
}
EOF

git add .
git commit -m "Initial commit"

# Create review directory and comments
mkdir -p .review
cat > .review/comments.json << 'EOF'
{
  "version": 1,
  "currentRound": "review-r1",
  "baseRef": "review-r0",
  "comments": [
    {
      "id": 1,
      "file": "src/Example.php",
      "line": 4,
      "ref": "review-r1",
      "status": "open",
      "resolveCommit": null,
      "thread": [
        {"author": "user", "text": "Add input validation", "at": "2024-01-23T10:00:00Z"}
      ]
    }
  ]
}
EOF

echo ""
echo "=== Testing CLI: list ==="
review list

echo ""
echo "=== Testing CLI: show ==="
review show 1

echo ""
echo "=== Testing CLI: reply ==="
review reply 1 "Should I throw an exception or return null?"

echo ""
echo "=== Testing CLI: list after reply ==="
review list

echo ""
echo "=== Testing CLI: fix ==="
# Make a commit first
echo "// validated" >> src/Example.php
git add .
git commit -m "Add validation"
COMMIT_SHA=$(git rev-parse --short HEAD)
review fix 1 --commit "$COMMIT_SHA"

echo ""
echo "=== Final state ==="
review list

echo ""
echo "=== Integration test PASSED ==="

# Cleanup
rm -rf "$TEMP_DIR"
