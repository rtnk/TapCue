#!/usr/bin/env bash
# Publishes a TapCue GitHub Release from a pushed tag, never over an existing one (STEP-07, docs/RELEASING.md).
#
#   scripts/fork/publish-release.sh check <tag>                         # fails if a Release for <tag> exists
#   scripts/fork/publish-release.sh publish <tag> <sha> <dir> <notes>  # creates it from <dir> (archive + SHA256SUMS)
#
# Uses `gh` with GH_TOKEN and GITHUB_REPOSITORY (the publish job of .github/workflows/release.yml). `publish` refuses to
# run unless the workflow was started by pushing <tag> (GITHUB_EVENT_NAME=push, GITHUB_REF_TYPE=tag, GITHUB_REF_NAME=<tag>),
# the tag still points at <sha> on GitHub, <dir> holds exactly tapcue-<version>-macos-arm64.zip and a SHA256SUMS that
# matches it, and no Release (draft or published) uses <tag>. It then creates the Release with `--verify-tag` (it never
# creates or moves a tag), downloads the assets back and checks them against SHA256SUMS. Nothing here deletes or edits
# a Release: a broken one is followed by a new patch version (docs/RELEASING.md, "Procedury awaryjne").
set -euo pipefail

repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

sha256_check() {
  if command -v sha256sum > /dev/null; then sha256sum -c SHA256SUMS; else shasum -a 256 -c SHA256SUMS; fi
}

# Every Release tag the token can see; with contents: write that includes drafts.
release_exists() {
  local tags
  tags="$(gh api --paginate "repos/$repo/releases?per_page=100" --jq '.[].tag_name')" ||
    fail "could not list the Releases of $repo"
  grep -Fxq -- "$1" <<<"$tags"
}

# The commit a tag points at on GitHub, following an annotated tag to its commit.
remote_tag_commit() {
  local ref type object
  ref="$(gh api "repos/$repo/git/ref/tags/$1" --jq '.object.type + " " + .object.sha')" ||
    fail "tag $1 does not exist in $repo"
  read -r type object <<<"$ref"
  while [ "$type" = tag ]; do
    ref="$(gh api "repos/$repo/git/tags/$object" --jq '.object.type + " " + .object.sha')" ||
      fail "could not read tag object $object"
    read -r type object <<<"$ref"
  done
  [ "$type" = commit ] || fail "tag $1 points at a $type, not a commit"
  echo "$object"
}

command="${1:-}"
case "$command" in
  check)
    tag="${2:?usage: $0 check <tag>}"
    if release_exists "$tag"; then
      fail "a Release for $tag already exists in $repo; Releases are never overwritten — release a new patch version"
    fi
    echo "OK: no Release for $tag in $repo"
    ;;

  publish)
    [ "$#" -eq 5 ] || fail "usage: $0 publish <tag> <sha> <dir> <notes>"
    tag="$2" sha="$3" dir="$4" notes="$5"
    [[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || fail "tag '$tag' is not vMAJOR.MINOR.PATCH"
    version="${BASH_REMATCH[1]}"
    if [ "${GITHUB_EVENT_NAME:-}" != push ] || [ "${GITHUB_REF_TYPE:-}" != tag ] || [ "${GITHUB_REF_NAME:-}" != "$tag" ]; then
      fail "publishing needs a run started by pushing $tag (event '${GITHUB_EVENT_NAME:-}', ref '${GITHUB_REF_TYPE:-}' '${GITHUB_REF_NAME:-}')"
    fi
    [ -s "$notes" ] || fail "release notes $notes are missing or empty"

    archive="tapcue-$version-macos-arm64.zip"
    [ -f "$dir/$archive" ] || fail "$dir has no $archive"
    [ -f "$dir/SHA256SUMS" ] || fail "$dir has no SHA256SUMS"
    extra="$(find "$dir" -mindepth 1 -maxdepth 1 ! -name "$archive" ! -name SHA256SUMS)"
    [ -z "$extra" ] || fail "$dir holds files that are not release assets: $extra"
    [ "$(awk '{print $2}' "$dir/SHA256SUMS")" = "$archive" ] || fail "SHA256SUMS does not list exactly $archive"
    (cd "$dir" && sha256_check) || fail "$archive does not match SHA256SUMS"

    target="$(remote_tag_commit "$tag")"
    [ "$target" = "$sha" ] || fail "tag $tag now points at $target, not at the built commit $sha"

    if release_exists "$tag"; then
      fail "a Release for $tag already exists in $repo; Releases are never overwritten — release a new patch version"
    fi

    gh release create "$tag" --repo "$repo" --verify-tag --title "TapCue $version" --notes-file "$notes" \
      "$dir/$archive" "$dir/SHA256SUMS"

    check="$(mktemp -d)"
    gh release download "$tag" --repo "$repo" --dir "$check"
    (cd "$check" && sha256_check) || fail "the assets downloaded from the Release do not match SHA256SUMS"
    cmp "$dir/$archive" "$check/$archive" && cmp "$dir/SHA256SUMS" "$check/SHA256SUMS" ||
      fail "the assets downloaded from the Release differ from the built ones"
    echo "OK: Release $tag published from $sha; the downloaded assets are the built ones"
    ;;

  *)
    sed -n '4,5p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
    ;;
esac
