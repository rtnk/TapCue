$pass = 0; $fail = 0

function Check([string]$desc, [string]$cmd, [string]$assertion, [string]$expected) {
    $actual = (Invoke-Expression "$cmd 2>&1" | Out-String).Trim()
    $ok = switch ($assertion) {
        'equals'   { $actual -eq $expected }
        'includes' { $actual -like "*$expected*" }
        'excludes' { $actual -notlike "*$expected*" }
        default    { Write-Error "unknown assertion '$assertion' (use: equals, includes, excludes)"; exit 1 }
    }
    if ($ok) {
        Write-Host "PASS: $desc"
        $script:pass++
    } else {
        Write-Host ("FAIL: {0}" -f $desc)
        Write-Host ("      {0,-11} {1}" -f "cmd:",       $cmd)
        Write-Host ("      {0,-11} {1}" -f "assertion:", $assertion)
        Write-Host ("      {0,-11} {1}" -f "expected:",  $expected)
        Write-Host ("      {0,-11} {1}" -f "got:",       $actual)
        $script:fail++
    }
}

$version = (Select-String -Path 'maestro-cli/gradle.properties' -Pattern '^CLI_VERSION=').Line `
    -replace '^CLI_VERSION=', ''

# TESTS START HERE:

Check "tapcue -v prints the name and gradle.properties version" `
    "tapcue -v" "includes" "tapcue $version"
Check "tapcue -v states its origin" `
    "tapcue -v" "includes" "based on Maestro (Apache-2.0), not affiliated with mobile.dev"
Check "tapcue gives usage instructions when called without parameters" `
    "tapcue" "includes" "Usage: tapcue"
Check "tapcue gives usage instructions when called with --help" `
    "tapcue --help" "includes" "Usage: tapcue"
Check "tapcue test subcommand gives usage instructions when called with --help" `
    "tapcue test --help" "includes" "Usage: tapcue test"
Check "tapcue bugreport gives instruction" `
    "tapcue bugreport" "includes" "https://github.com/rtnk/TapCue/issues"

Write-Host ""
Write-Host "$pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
exit 0
