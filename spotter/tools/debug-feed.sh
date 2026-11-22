#!/usr/bin/env bash
# Prints spotter-debug.json for one CI debug build: what the app's updater reads
# (app/src/main/kotlin/spotter/app/update/UpdateFeed.kt). CI runs it; a test runs it too, so the
# two can't drift apart.
#   tools/debug-feed.sh <apk> <build number> <commit>
set -euo pipefail
apk="$1"; build="$2"; commit="$3"
[[ "$build" =~ ^[0-9]+$ ]] || { echo "build must be a number: $build" >&2; exit 1; }
[[ "$commit" =~ ^[0-9a-f]{7,40}$ ]] || { echo "commit must be hex: $commit" >&2; exit 1; }
size=$(wc -c < "$apk" | tr -d ' ')
sha=$(sha256sum "$apk" | cut -d' ' -f1)
url="https://github.com/madCode/claude-playground/releases/download/spotter-debug/spotter-debug-$build.apk"
printf '{"build":%s,"commit":"%s","apk":"%s","size":%s,"sha256":"%s"}\n' "$build" "$commit" "$url" "$size" "$sha"
