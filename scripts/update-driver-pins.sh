#!/usr/bin/env bash
#
# Populate missing sha256 pins in jmcp-jdbc/src/main/resources/known_drivers.json.
#
# For every artifact entry that has no "sha256" attribute (bare "g:a:v" strings
# and {"gav": ...} objects alike), this script:
#   1. downloads the jar from Maven Central,
#   2. computes its SHA-256 locally,
#   3. cross-checks the download against the repository's published checksum
#      (.sha256 when available, .sha1 otherwise - Central does not publish
#      .sha256 for every artifact),
#   4. rewrites the entry as {"gav": "...", "sha256": "..."}.
#
# Entries that already carry a sha256 pin are skipped and left untouched.
# On any checksum cross-check mismatch the script aborts without writing.
#
# Note the trust model: a pin is only as good as the artifact it was computed
# from (trust-on-first-pin). Run this from a network you trust.
#
# Requires: jq, curl, and sha256sum or shasum.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON="$REPO_ROOT/jmcp-jdbc/src/main/resources/known_drivers.json"
BASE="https://repo1.maven.org/maven2"

command -v jq >/dev/null || { echo "error: jq is required" >&2; exit 1; }
command -v curl >/dev/null || { echo "error: curl is required" >&2; exit 1; }
if command -v sha256sum >/dev/null; then
    sha256_of() { sha256sum "$1" | awk '{print $1}'; }
elif command -v shasum >/dev/null; then
    sha256_of() { shasum -a 256 "$1" | awk '{print $1}'; }
else
    echo "error: sha256sum or shasum is required" >&2; exit 1
fi

[ -f "$JSON" ] || { echo "error: $JSON not found" >&2; exit 1; }

# ${var,,} needs bash 4+; macOS ships bash 3.2
lc() { tr '[:upper:]' '[:lower:]' <<< "$1"; }

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

updated=$(cat "$JSON")
pinned=0
skipped=0

# Emit one "type <TAB> index <TAB> gav" line per artifact entry, with the gav
# extracted from either entry form. Entries with a sha256 are marked SKIP.
entries=$(jq -r '
    to_entries[] | .key as $type
    | .value | to_entries[]
    | [$type,
       (.key | tostring),
       (if (.value | type) == "string" then .value else .value.gav end),
       (if (.value | type) == "object" and ((.value.sha256? // "") != "") then "SKIP" else "PIN" end)]
    | @tsv' "$JSON")

while IFS=$'\t' read -r type idx gav action; do
    if [ "$action" = "SKIP" ]; then
        echo "already pinned: $type[$idx] $gav"
        skipped=$((skipped + 1))
        continue
    fi

    IFS=: read -r group artifact version <<< "$gav"
    if [ -z "$group" ] || [ -z "$artifact" ] || [ -z "$version" ]; then
        echo "error: malformed coordinates '$gav' for type '$type'" >&2
        exit 1
    fi

    jar_url="$BASE/${group//.//}/$artifact/$version/$artifact-$version.jar"
    jar_file="$workdir/$artifact-$version.jar"

    echo "pinning: $type[$idx] $gav"
    curl -fsSL "$jar_url" -o "$jar_file"
    local_sha256=$(sha256_of "$jar_file")

    # Cross-check the downloaded bytes against the repository's published
    # checksum before trusting them enough to pin.
    if repo_sha256=$(curl -fsSL "$jar_url.sha256" 2>/dev/null | awk '{print $1}') \
            && [ -n "$repo_sha256" ]; then
        if [ "$(lc "$local_sha256")" != "$(lc "$repo_sha256")" ]; then
            echo "error: sha256 cross-check MISMATCH for $gav" >&2
            echo "  local:      $local_sha256" >&2
            echo "  repository: $repo_sha256" >&2
            exit 1
        fi
    else
        # No .sha256 published for this artifact - fall back to .sha1.
        local_sha1=$(shasum -a 1 "$jar_file" | awk '{print $1}')
        repo_sha1=$(curl -fsSL "$jar_url.sha1" | awk '{print $1}')
        if [ "$(lc "$local_sha1")" != "$(lc "$repo_sha1")" ]; then
            echo "error: sha1 cross-check MISMATCH for $gav" >&2
            echo "  local:      $local_sha1" >&2
            echo "  repository: $repo_sha1" >&2
            exit 1
        fi
        echo "  (no .sha256 published; cross-checked via .sha1)"
    fi

    updated=$(jq --arg type "$type" --argjson idx "$idx" \
                 --arg gav "$gav" --arg sha "$local_sha256" \
                 '.[$type][$idx] = {gav: $gav, sha256: $sha}' <<< "$updated")
    pinned=$((pinned + 1))
done <<< "$entries"

if [ "$pinned" -gt 0 ]; then
    jq . <<< "$updated" > "$JSON"
fi
echo "done: $pinned pinned, $skipped already pinned - $JSON"
