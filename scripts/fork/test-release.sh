#!/usr/bin/env bash
# Tests for the STEP-07 release scripts: scripts/fork/release-context.sh, scripts/fork/release-notes.py and
# scripts/fork/publish-release.sh. Each negative case plants one problem and expects the script to fail with a message
# naming it; the publish cases run against a fake `gh` that records its calls, and must not create a Release.
#
#   scripts/fork/test-release.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

expect_fail() {
  local name="$1" pattern="$2"
  shift 2
  if output="$("$@" 2>&1)"; then
    echo "FAIL: $name was accepted"
    echo "$output"
    exit 1
  fi
  if ! grep -Eq -- "$pattern" <<<"$output"; then
    echo "FAIL: $name was rejected, but not for /$pattern/:"
    echo "$output"
    exit 1
  fi
  echo "ok: $name rejected"
}

expect_output() {
  local name="$1" pattern="$2"
  shift 2
  if ! output="$("$@" 2>&1)"; then
    echo "FAIL: $name failed:"
    echo "$output"
    exit 1
  fi
  if ! grep -Eq -- "$pattern" <<<"$output"; then
    echo "FAIL: $name: output does not match /$pattern/:"
    echo "$output"
    exit 1
  fi
  echo "ok: $name"
}

# --- release-context.sh -------------------------------------------------------------------------------------------
repo="$work/repo"
mkdir -p "$repo/scripts/fork" "$repo/maestro-cli"
cp "$root/scripts/fork/release-context.sh" "$repo/scripts/fork/"
git -C "$repo" init -q -b main
git -C "$repo" config user.email test@example.com
git -C "$repo" config user.name test
echo "CLI_VERSION=0.1.0" > "$repo/maestro-cli/gradle.properties"
git -C "$repo" add -A
git -C "$repo" commit -q -m main
on_main="$(git -C "$repo" rev-parse HEAD)"
git -C "$repo" tag -a v0.1.0 -m "TapCue 0.1.0"
git -C "$repo" tag v0.2.0
git -C "$repo" tag -a v0.1.1 -m "wrong version"
git -C "$repo" tag -a v0.1 -m "bad format"
git -C "$repo" checkout -q -b side
git -C "$repo" commit -q --allow-empty -m side
off_main="$(git -C "$repo" rev-parse HEAD)"
git -C "$repo" checkout -q main

context() { (cd "$repo" && env MAIN_REF=main "$@" scripts/fork/release-context.sh); }

expect_output "workflow_dispatch is a validation run" '^mode=validate$' \
  context GITHUB_EVENT_NAME=workflow_dispatch GITHUB_REF_TYPE=branch GITHUB_REF_NAME=main GITHUB_SHA="$on_main"
expect_output "a validation run names the tag it would use" '^tag=v0\.1\.0$' \
  context GITHUB_EVENT_NAME=workflow_dispatch GITHUB_REF_TYPE=branch GITHUB_REF_NAME=main GITHUB_SHA="$on_main"
expect_output "a pull request is a validation run" '^mode=validate$' \
  context GITHUB_EVENT_NAME=pull_request GITHUB_REF_TYPE=branch GITHUB_REF_NAME=1/merge GITHUB_SHA="$on_main"
expect_output "an annotated tag on main matching CLI_VERSION publishes" '^mode=publish$' \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1.0 GITHUB_SHA="$on_main"
expect_fail "a lightweight tag" 'not an annotated tag' \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.2.0 GITHUB_SHA="$on_main"
expect_fail "a tag that is not vMAJOR.MINOR.PATCH" 'is not vMAJOR\.MINOR\.PATCH' \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1 GITHUB_SHA="$on_main"
expect_fail "a tag that does not match CLI_VERSION" 'does not match CLI_VERSION 0\.1\.0' \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1.1 GITHUB_SHA="$on_main"
expect_fail "a checkout that is not the built commit" 'not at the commit being released' \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1.0 GITHUB_SHA="$off_main"
git -C "$repo" tag -d v0.1.0 > /dev/null
git -C "$repo" tag -a v0.1.0 -m "TapCue 0.1.0 off main" "$off_main"
expect_fail "a tag that points at another commit" "points at $off_main, not at the commit being built" \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1.0 GITHUB_SHA="$on_main"
git -C "$repo" checkout -q side
expect_fail "a tag on a commit that is not on main" 'is not on main' \
  context GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1.0 GITHUB_SHA="$off_main"
git -C "$repo" checkout -q main
echo "CLI_VERSION=0.1" > "$repo/maestro-cli/gradle.properties"
expect_fail "a CLI_VERSION that is not MAJOR.MINOR.PATCH" 'is not MAJOR\.MINOR\.PATCH' \
  context GITHUB_EVENT_NAME=workflow_dispatch GITHUB_REF_TYPE=branch GITHUB_REF_NAME=main GITHUB_SHA="$on_main"
git -C "$repo" checkout -q -- maestro-cli/gradle.properties

# --- release-notes.py ---------------------------------------------------------------------------------------------
cat > "$work/CHANGELOG.md" <<'MD'
# Changelog

## TapCue (fork)

### Unreleased

- something pending

### 0.1.0 — 2026-11-01

- First local-only release.

#### Known limitations

- Only macOS arm64 is tested.

### 0.0.9

- Nothing about limitations here.

## Upstream Maestro

### 2.11.0

- Upstream release.
MD
printf '%s  %s\n' 0123abcd tapcue-0.1.0-macos-arm64.zip > "$work/SHA256SUMS"
printf 'commit=%s\njdk=%s\n' abc123 'Zulu 17.0.21' > "$work/build-info.txt"
notes() { python3 -I "$root/scripts/fork/release-notes.py" --changelog "$work/CHANGELOG.md" "$@"; }

expect_output "notes take the version's section" 'First local-only release' notes 0.1.0 --require-section
expect_output "notes keep the limitations" 'Only macOS arm64 is tested' notes 0.1.0 --require-section
if notes 0.1.0 | grep -q 'Nothing about limitations\|something pending\|Upstream release'; then
  echo "FAIL: notes include text from other sections"
  exit 1
fi
echo "ok: notes stop at the next section"
expect_output "notes carry the origin notice (D-1)" 'based on Maestro \(Apache-2\.0\) and is not affiliated with mobile\.dev' notes 0.1.0
expect_output "notes carry the archive's SHA-256" '`tapcue-0\.1\.0-macos-arm64\.zip`: `0123abcd`' notes 0.1.0 --sums "$work/SHA256SUMS"
expect_output "notes carry the build facts" 'jdk: `Zulu 17\.0\.21`' notes 0.1.0 --build-info "$work/build-info.txt"
expect_output "a validation run without a section gets a placeholder" 'No `### 0\.2\.0` section' notes 0.2.0
expect_fail "a missing section when publishing" "no non-empty '### 0\.2\.0' section" notes 0.2.0 --require-section
expect_fail "an upstream section of the same version" "no non-empty '### 2\.11\.0' section" notes 2.11.0 --require-section
expect_fail "a section without known limitations (G-8)" "no 'Known limitations'" notes 0.0.9 --require-section
printf '\n### 0.1.0\n\n- again\n' > "$work/dup.md"
sed -i "/^### 0.0.9/e cat $work/dup.md" "$work/CHANGELOG.md"
expect_fail "a duplicated section" "2 '### 0\.1\.0' sections" notes 0.1.0 --require-section

# --- publish-release.sh with a fake gh ----------------------------------------------------------------------------
mkdir -p "$work/bin" "$work/dist"
cat > "$work/bin/gh" <<'GH'
#!/usr/bin/env bash
# Fake gh: FAKE_RELEASES (tags of existing Releases), FAKE_TAG (tag the API knows), FAKE_TAG_COMMIT, FAKE_LIST_FAIL.
echo "gh $*" >> "$FAKE_LOG"
case "$*" in
  "api --paginate repos/"*"/releases?per_page=100 "*)
    [ -z "${FAKE_LIST_FAIL:-}" ] || { echo "HTTP 500" >&2; exit 1; }
    printf '%s' "${FAKE_RELEASES:-}" ;;
  "api repos/"*"/git/ref/tags/"*)
    ref="${2##*/}"
    [ "$ref" = "$FAKE_TAG" ] || { echo "HTTP 404" >&2; exit 1; }
    echo "tag tagobject1" ;;
  "api repos/"*"/git/tags/tagobject1 "*)
    echo "commit $FAKE_TAG_COMMIT" ;;
  "release create "*)
    shift 2
    mkdir -p "$FAKE_RELEASED"
    for arg in "$@"; do [ -f "$arg" ] && cp "$arg" "$FAKE_RELEASED/"; done ;;
  "release download "*)
    while [ "$1" != --dir ]; do shift; done
    dir="$2"
    cp "$FAKE_RELEASED"/* "$dir/" ;;
  *) echo "fake gh: unexpected call: $*" >&2; exit 1 ;;
esac
GH
chmod +x "$work/bin/gh"
printf 'archive bytes' > "$work/dist/tapcue-0.1.0-macos-arm64.zip"
(cd "$work/dist" && sha256sum tapcue-0.1.0-macos-arm64.zip > SHA256SUMS)
printf 'notes\n' > "$work/notes.md"
sha=1111111111111111111111111111111111111111

publish() {
  rm -f "$work/gh.log"
  rm -rf "$work/released"
  : > "$work/gh.log"
  env PATH="$work/bin:$PATH" FAKE_LOG="$work/gh.log" FAKE_RELEASED="$work/released" FAKE_TAG=v0.1.0 \
    FAKE_TAG_COMMIT="$sha" GITHUB_REPOSITORY=rtnk/TapCue GITHUB_EVENT_NAME=push GITHUB_REF_TYPE=tag \
    GITHUB_REF_NAME=v0.1.0 "$@"
}
no_release_created() {
  if grep -q "release create" "$work/gh.log"; then
    echo "FAIL: a Release was created:"
    cat "$work/gh.log"
    exit 1
  fi
}
script="$root/scripts/fork/publish-release.sh"

expect_output "check passes when there is no Release" 'OK: no Release for v0\.1\.0' publish FAKE_RELEASES=$'v0.0.9\n' "$script" check v0.1.0
expect_fail "check with an existing Release" 'already exists' publish FAKE_RELEASES=$'v0.0.9\nv0.1.0\n' "$script" check v0.1.0
expect_fail "check when the Releases cannot be listed" 'could not list' publish FAKE_LIST_FAIL=1 "$script" check v0.1.0

expect_fail "publishing over an existing Release" 'already exists' \
  publish FAKE_RELEASES=$'v0.1.0\n' "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
no_release_created
expect_fail "publishing from workflow_dispatch" 'needs a run started by pushing v0\.1\.0' \
  publish GITHUB_EVENT_NAME=workflow_dispatch GITHUB_REF_TYPE=branch GITHUB_REF_NAME=main \
  "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
[ ! -s "$work/gh.log" ] || { echo "FAIL: gh was called in a validation run"; cat "$work/gh.log"; exit 1; }
expect_fail "publishing from a pull request" 'needs a run started by pushing' \
  publish GITHUB_EVENT_NAME=pull_request GITHUB_REF_TYPE=branch "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
no_release_created
expect_fail "publishing a tag other than the pushed one" 'needs a run started by pushing v0\.1\.1' \
  publish "$script" publish v0.1.1 "$sha" "$work/dist" "$work/notes.md"
no_release_created
expect_fail "publishing after the tag moved" 'now points at 2222' \
  publish FAKE_TAG_COMMIT=2222222222222222222222222222222222222222 "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
no_release_created
expect_fail "publishing a tag GitHub does not have" 'does not exist' \
  publish FAKE_TAG=v9.9.9 "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
no_release_created
expect_fail "publishing when the Releases cannot be listed" 'could not list' \
  publish FAKE_LIST_FAIL=1 "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
no_release_created
expect_fail "publishing without notes" 'notes .* missing or empty' \
  publish "$script" publish v0.1.0 "$sha" "$work/dist" "$work/missing.md"
no_release_created
cp -r "$work/dist" "$work/dist-bad-sum"
printf 'other bytes' > "$work/dist-bad-sum/tapcue-0.1.0-macos-arm64.zip"
expect_fail "publishing an archive that does not match SHA256SUMS" 'does not match SHA256SUMS' \
  publish "$script" publish v0.1.0 "$sha" "$work/dist-bad-sum" "$work/notes.md"
no_release_created
cp -r "$work/dist" "$work/dist-extra"
touch "$work/dist-extra/notes.md"
expect_fail "publishing a directory with extra files" 'not release assets' \
  publish "$script" publish v0.1.0 "$sha" "$work/dist-extra" "$work/notes.md"
no_release_created
expect_fail "publishing an archive of another version" 'has no tapcue-0\.1\.1-macos-arm64\.zip' \
  publish GITHUB_REF_NAME=v0.1.1 "$script" publish v0.1.1 "$sha" "$work/dist" "$work/notes.md"
no_release_created

expect_output "publishing a new tag" 'OK: Release v0\.1\.0 published' \
  publish FAKE_RELEASES=$'v0.0.9\n' "$script" publish v0.1.0 "$sha" "$work/dist" "$work/notes.md"
grep -q -- "release create v0.1.0 --repo rtnk/TapCue --verify-tag --title TapCue 0.1.0 --notes-file $work/notes.md" "$work/gh.log" ||
  { echo "FAIL: unexpected release create call:"; cat "$work/gh.log"; exit 1; }
echo "ok: the Release is created with --verify-tag from the existing tag"

echo "OK: release script tests passed"
