#!/usr/bin/env bash
# scripts/release.sh
#
# release-please runs in CI on every push to main. It opens / updates a
# "release PR" automatically based on Conventional Commits since the last
# release. There's nothing to "trigger" locally — this helper just shows
# the Conventional Commits cheatsheet and points at the open release PR.
set -euo pipefail

cat <<'EOF'
release-please is push-driven. To cut a new version:

  1. Commit with a Conventional Commit message:

       feat:     new user-facing feature        -> bumps MINOR
       fix:      bug fix                        -> bumps PATCH
       feat!:    breaking change                -> bumps MAJOR (post-1.0)
                  or BREAKING CHANGE: in body
       perf:     performance improvement        -> bumps PATCH
       chore:, docs:, refactor:, test:, ci:, build:, style:
                                                -> no version bump

     Example:
       git commit -m "feat(voice): add wake-word toggle"

  2. Push to main:
       git push origin main

  3. CI opens (or updates) a "chore(main): release X.Y.Z" PR.
     Review CHANGELOG.md + version.txt in that PR; merging it tags the
     release, which triggers the build job to attach the signed APK.

EOF

# Best-effort: show the current open release PR, if gh is available.
if command -v gh >/dev/null 2>&1; then
  echo "Open release PR(s):"
  gh pr list --search 'in:title "chore(main): release"' --state open \
     --json number,title,url \
     --jq '.[] | "  #\(.number)  \(.title)\n    \(.url)"' || true
fi
