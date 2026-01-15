#!/usr/bin/env bash
set -euo pipefail

# Generates app/src/main/assets/changelog.md from git tags and commit messages.
# - Commits after the latest tag are grouped under "UNRELEASED".
# - Each tag section lists commits between the previous tag and the tag.

OUT_FILE="app/src/main/assets/changelog.md"

mkdir -p "$(dirname "$OUT_FILE")"

echo "Generating changelog into $OUT_FILE"

# Get tags sorted by creation date (newest first)
mapfile -t tags < <(git for-each-ref --sort=-creatordate --format='%(refname:short)' refs/tags)

write_header() {
  cat > "$OUT_FILE" <<EOF
# Changelog

EOF
}

write_unreleased() {
  local newest_tag="$1"
  if [ -z "$newest_tag" ]; then
    # no tags at all: treat all commits as UNRELEASED
    commits=$(git log --pretty=format:'- %s (%h)' | grep -v -i -E 'release|version' || true)
  else
    commits=$(git log --pretty=format:'- %s (%h)' "$newest_tag"..HEAD | grep -v -i -E 'release|version' || true)
  fi

  if [ -n "$commits" ]; then
    cat >> "$OUT_FILE" <<EOF
## UNRELEASED
$commits

EOF
  fi
}

write_tag_section() {
  local tag="$1"
  local since_ref="$2" # older tag or root
  local tag_date
  tag_date=$(git log -1 --format='%ad' --date=short "$tag" 2>/dev/null || echo "")

  # Determine commits between since_ref and tag (since_ref..tag)
  if [ -n "$since_ref" ]; then
    commits=$(git log --pretty=format:'- %s (%h)' "$since_ref".."$tag" | grep -v -i -E 'release|version' || true)
  else
    # from repository root to this tag
    root=$(git rev-list --max-parents=0 HEAD || true)
    if [ -n "$root" ]; then
      commits=$(git log --pretty=format:'- %s (%h)' "$root".."$tag" | grep -v -i -E 'release|version' || true)
    else
      commits=""
    fi
  fi

  if [ -n "$commits" ]; then
    if [ -z "$tag_date" ]; then
      header="$tag"
    else
      header="$tag - $tag_date"
    fi

    cat >> "$OUT_FILE" <<EOF
## $header
EOF

    echo "$commits" >> "$OUT_FILE"
    echo >> "$OUT_FILE"
  fi
}

main() {
  write_header

  if [ ${#tags[@]} -eq 0 ]; then
    write_unreleased ""
    exit 0
  fi

  newest_tag=${tags[0]}
  write_unreleased "$newest_tag"

  # iterate tags from newest to oldest, writing sections for each tag
  for i in "${!tags[@]}"; do
    tag="${tags[$i]}"
    # since_ref is next tag in array (older)
    if [ $i -lt $((${#tags[@]} - 1)) ]; then
      since_ref="${tags[$((i+1))]}"
    else
      since_ref=""
    fi
    write_tag_section "$tag" "$since_ref"
  done

  echo "Wrote $OUT_FILE"
}

main
