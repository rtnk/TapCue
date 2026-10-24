#!/usr/bin/env bash
# Decides what a run of .github/workflows/release.yml may do (STEP-07, docs/RELEASING.md) and checks the tag.
#
#   scripts/fork/release-context.sh      # in a checkout of the commit being built, with origin/main and the tags fetched
#
# Reads the GitHub Actions variables GITHUB_EVENT_NAME, GITHUB_REF_TYPE, GITHUB_REF_NAME and GITHUB_SHA (MAIN_REF, default
# origin/main, names the main branch) and prints key=value lines for $GITHUB_OUTPUT:
#
#   mode     publish — only for a pushed tag vMAJOR.MINOR.PATCH; validate — everything else (workflow_dispatch, ...)
#   version  CLI_VERSION from maestro-cli/gradle.properties: the archive is tapcue-<version>-macos-arm64.zip
#   tag      the pushed tag (publish) or the tag this commit would be released under, v<version> (validate)
#   sha      the commit being built
#   on_main  true when the commit is on the main branch
#
# In publish mode it fails unless the tag is an annotated tag vMAJOR.MINOR.PATCH, points at the checked-out commit, equals
# v<CLI_VERSION>, and the commit is on main. In validate mode those facts are only reported.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
event="${GITHUB_EVENT_NAME:?GITHUB_EVENT_NAME is not set}"
ref_type="${GITHUB_REF_TYPE:-}"
ref_name="${GITHUB_REF_NAME:-}"
sha="${GITHUB_SHA:?GITHUB_SHA is not set}"
main="${MAIN_REF:-origin/main}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

head="$(git -C "$root" rev-parse HEAD)"
[ "$head" = "$sha" ] || fail "the checkout is at $head, not at the commit being released ($sha)"

version="$(sed -n 's/^CLI_VERSION=//p' "$root/maestro-cli/gradle.properties")"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "CLI_VERSION '$version' in maestro-cli/gradle.properties is not MAJOR.MINOR.PATCH"

git -C "$root" rev-parse --verify --quiet "$main^{commit}" > /dev/null || fail "$main is not available; fetch the main branch first"
if git -C "$root" merge-base --is-ancestor "$sha" "$main"; then
  on_main=true
else
  on_main=false
fi

if [ "$event" = push ] && [ "$ref_type" = tag ]; then
  mode=publish
  tag="$ref_name"
  [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "tag '$tag' is not vMAJOR.MINOR.PATCH"
  [ "$(git -C "$root" cat-file -t "refs/tags/$tag" 2>/dev/null)" = tag ] ||
    fail "tag $tag is not an annotated tag (create it with git tag -a)"
  target="$(git -C "$root" rev-parse "refs/tags/$tag^{commit}")"
  [ "$target" = "$sha" ] || fail "tag $tag points at $target, not at the commit being built ($sha)"
  [ "$tag" = "v$version" ] || fail "tag $tag does not match CLI_VERSION $version (expected v$version)"
  [ "$on_main" = true ] || fail "tag $tag points at $sha, which is not on $main"
else
  mode=validate
  tag="v$version"
fi

echo "mode=$mode"
echo "version=$version"
echo "tag=$tag"
echo "sha=$sha"
echo "on_main=$on_main"
