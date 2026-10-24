# TapCue flow cheat sheet

Written for TapCue from the flow parser in this repository (`maestro-orchestra`, package
`maestro.orchestra.yaml`). TapCue is based on Maestro and reads the same YAML flow format. Every `yaml`
example below is parsed by a unit test, so it is valid syntax.

## Flow file layout

A flow is a YAML file with a config section, a `---` separator and a list of commands.

```yaml
appId: com.example.app        # Android package or iOS bundle id (required)
name: Login works             # optional display name
tags:
  - smoke                     # used by `include_tags` / `exclude_tags` (no `@`)
env:
  USERNAME: alice             # available as ${USERNAME}
---
- launchApp
- tapOn: "Username"
- inputText: ${USERNAME}
- tapOn: "Log in"
- assertVisible: "Welcome"
```

Web flows (`url:` instead of `appId:`) are not supported in TapCue 0.1.0.

Keep a project's flows in its `.tapcue/` directory (TapCue does not read `.maestro/`; rename it with
`mv .maestro .tapcue`) and run them with `tapcue test .tapcue`.

Every command accepts `label` (text shown in reports) and `optional: true` (a failure becomes a
warning). Variables use `${NAME}`; values come from `env` in the config, `env` passed to `run`,
`runFlow`/`runScript` `env`, or JavaScript `output.*`.

## Selectors

Commands that target an element (`tapOn`, `doubleTapOn`, `longPressOn`, `assertVisible`,
`assertNotVisible`, `copyTextFrom`, conditions, `scrollUntilVisible.element`) take a selector.
A plain string matches visible text (a regular expression, matched against the whole text).

```yaml
appId: com.example.app
---
- tapOn: "Sign in"                  # text
- tapOn:
    id: "login_button"              # resource id / accessibility identifier (regex)
- tapOn:
    text: "Item.*"
    index: 2                        # third match, 0-based
- tapOn:
    text: "Delete"
    below: "Inbox"                  # relative: below, above, leftOf, rightOf
- tapOn:
    id: "row"
    containsChild: "Unread"         # also: containsDescendants (list), childOf
- assertVisible:
    text: "Submit"
    enabled: true                   # also: checked, focused, selected
- tapOn:
    point: "50%,90%"                # relative screen point
- tapOn:
    point: "120,640"                # absolute pixels
```

Inspect the screen (`inspect_screen`) before choosing a selector; prefer `id` over text when
both exist, and use `index` only as a last resort.

## Interaction

```yaml
appId: com.example.app
---
- launchApp
- launchApp:
    appId: com.example.other
    clearState: true                # wipe app data before launch
    clearKeychain: true             # iOS only
    stopApp: false                  # keep a running instance
    permissions:
      all: deny                     # or: allow / unset, per permission name
    arguments:
      featureFlag: "on"
- tapOn: "OK"
- tapOn:
    text: "Next"
    repeat: 3
    delay: 500                      # ms between taps
    retryTapIfNoChange: false
- doubleTapOn: "Photo"
- longPressOn: "Message"
- inputText: "hello@example.com"
- inputRandomEmail
- inputRandomText:
    length: 8
- inputRandomNumber:
    length: 4
- inputRandomPersonName
- eraseText                         # default 50 characters
- eraseText: 10
- pasteText
- copyTextFrom:
    id: "otp_code"                  # copied text is in ${maestro.copiedText}
- setClipboard: "copied value"
- hideKeyboard
- pressKey: Enter                   # also Backspace, Back, Home, Lock, Tab, Escape, Power, Volume Up, Volume Down
- back                              # Android back button
- scroll                            # one screen down
- swipe:
    direction: UP                   # UP, DOWN, LEFT, RIGHT
- swipe:
    start: "50%,80%"
    end: "50%,20%"
    duration: 400
- swipe:
    from:
      id: "carousel"
    direction: LEFT
- scrollUntilVisible:
    element: "Terms of service"
    direction: DOWN
    timeout: 20000
    centerElement: true
- openLink: "myapp://settings"      # the device opens the link, not the host
- stopApp
- killApp
- clearState
- clearKeychain
- setPermissions:
    permissions:
      camera: allow
- setLocation:
    latitude: "52.2297"
    longitude: "21.0122"
- travel:
    points:
      - "52.2297,21.0122"
      - "52.2400,21.0200"
    speed: 7.5
- setOrientation: LANDSCAPE_LEFT    # PORTRAIT, LANDSCAPE_LEFT, LANDSCAPE_RIGHT, UPSIDE_DOWN
- setAirplaneMode: enabled          # or disabled
- toggleAirplaneMode
- setDarkMode: enabled
- toggleDarkMode
- waitForAnimationToEnd
```

## Assertions and waiting

```yaml
appId: com.example.app
---
- assertVisible: "Welcome"
- assertNotVisible: "Error"
- assertTrue: ${output.count > 0}
- extendedWaitUntil:
    visible: "Dashboard"            # or notVisible
    timeout: 10000                  # ms
- assertDarkMode
- assertLightMode
- takeScreenshot: home_screen       # saved as home_screen.png
- assertScreenshot: home_screen     # compares against home_screen.png
- assertScreenshot:
    path: home_screen
    thresholdPercentage: 98
    cropOn:
      id: "header"
```

`assertScreenshot` always needs a name; a bare `- assertScreenshot` fails to parse.

## Flow control

```yaml
appId: com.example.app
---
- runFlow:
    when:
      visible: "Accept cookies"     # also: notVisible, platform, true
    commands:
      - tapOn: "Accept"
- runFlow:
    when:
      platform: Android
    commands:
      - back
- repeat:
    times: 3
    commands:
      - scroll
- repeat:
    while:
      notVisible: "End of list"
    commands:
      - scroll
- retry:
    maxRetries: 2
    commands:
      - tapOn: "Refresh"
      - assertVisible: "Updated"
```

Reusable flows live in separate files and are called with `runFlow: login.yaml` or
`runFlow: { file: login.yaml, env: { USERNAME: bob } }` (paths are relative to the calling flow).
`retry` also accepts `file:` instead of `commands:`.

Config hooks run before and after the commands:

```yaml
appId: com.example.app
onFlowStart:
  - clearState
onFlowComplete:
  - stopApp
---
- launchApp
```

## JavaScript

```yaml
appId: com.example.app
---
- evalScript: ${output.greeting = 'Hello ' + USERNAME}
- inputText: ${output.greeting}
- runFlow:
    when:
      true: ${maestro.platform == 'android'}
    commands:
      - back
```

`runScript: script.js` (or `{ file: script.js, env: { ... } }`) runs a file. Scripts can read flow
variables, write `output.<name>` for later commands, and use `maestro.platform`,
`maestro.copiedText`, `faker` and `http`.

`http.get(url, { headers })`, `http.post(url, { body, headers, multipartForm })`, `http.put`,
`http.delete` and `http.request(url, { method, ... })` return `{ ok, status, body, headers }`.
In TapCue they may only reach loopback (`127.0.0.0/8`, `::1`), private networks
(`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `fc00::/7`) and link-local addresses; any other
address, including after a redirect, fails the script.

## Media and recording

```yaml
appId: com.example.app
---
- startRecording: checkout
- stopRecording
```

`addMedia: [ "photo.png" ]` pushes files from the flow directory to the device gallery.

## Running flows through MCP

- `list_devices` first; use only the `device_id` values it returns.
- `inspect_screen` before writing selectors; re-inspect after the UI changes.
- `run` takes exactly one of `yaml` (inline flow, config section optional), `files` or `dir`
  (with optional `include_tags` / `exclude_tags`), plus `device_id` and optional `env`.
- Prefer one complete flow per `run` call over many single-command calls.
