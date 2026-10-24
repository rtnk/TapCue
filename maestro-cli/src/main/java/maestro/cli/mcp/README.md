# Maestro MCP Server

## Overview

The Maestro MCP (Model Context Protocol) server enables LLM-driven automation and orchestration of Maestro commands and device management. It serves two primary functions for calling LLMs:
- enables LLMs to directly control and interact with devices using Maestro device capabilities
- enables LLMs to write, validate, and run Maestro code (flows)

The MCP server is designed to be extensible, maintainable, and easy to run as part of the Maestro CLI. It supports real-time device management, app automation, and more, all via a standardized protocol.

## Features

- Exposes Maestro device and automation commands as MCP tools
- Supports listing, launching, and interacting with devices
- Supports running inline flow YAML, specific test files, or entire flow directories (syntax is validated on execution)
- Easily extensible: add new tools with minimal boilerplate
- Includes a test script and config for automated validation

## Running the MCP Server

To use the MCP server as an end user, after installing TapCue run:

```
tapcue mcp
```

This launches the MCP server via the Maestro CLI, exposing Maestro tools over STDIO for LLM agents and other clients.

TapCue's MCP server is local-only. Its tools are `list_devices`, `inspect_screen`,
`take_screenshot`, `run` and `cheat_sheet`; they talk to devices over ADB and the local XCTest runner and
make no requests to remote services. `cheat_sheet` returns `src/main/resources/mcp/cheat-sheet.md`, which
ships in the CLI JAR (upstream fetched it from Maestro's API). Every `yaml` example in it is parsed by
`CheatSheetToolTest`, so keep them valid when editing it.

The Maestro Viewer is not part of TapCue: it depends on the simulator-server binary, which may only
be distributed with official Maestro releases (`docs/LICENSE-AUDIT.md` L-01). Its sources were removed in
STEP-04; `--no-viewer` and `--viewer-port` are still accepted and ignored so existing client configurations
keep working.

## Developing

## Extending the MCP Server

To add a new tool:
1. Create a new file in `maestro-cli/src/main/java/maestro/cli/mcp/tools/` following the same patterns as the other tools.
2. Add your tool to the `addTools` call in `McpServer.kt`
3. Build the CLI with `./gradlew :maestro-cli:installDist`
4. Add the tool to the expected list in `McpStdioTest`, which starts `tapcue mcp` (the CLI main class) as a process and drives it
   over STDIO (`initialize`, `tools/list`, `tools/call`) with all outbound connections recorded

## Evals testing

The upstream LLM evaluations in `maestro-cli/src/test/mcp/` run `npx mcp-server-tester` against remote model
APIs. They are not part of the TapCue build or CI (`docs/NETWORK-AUDIT.md` B-08); `McpStdioTest` is the
local protocol test.

## Implementation Notes & Rationale

### Using forked version of official kotlin MCP SDK

The [official MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) can't be used directly because it requires Java 21 and Kotlin 2.x, while Maestro is built on Java 8 and Kotlin 1.8.x for broad compatibility. However, we want to be able to benefit from features added to the SDK since the MCP spec is changing rapidly. So we created a fork that "downgrades" the reference SDK to Java 8 and Kotlin 1.8.22.


### Why Integrate MCP Server Directly Into `maestro-cli`?

- **Dependency Management:** The MCP server needs access to abstractions like `MaestroSessionManager` and other CLI internals. Placing it in a separate module (e.g., `maestro-mcp`) would create a circular dependency between `maestro-cli` and the new module.
- **Simplicity:** Keeping all MCP logic within `maestro-cli` avoids complex build configurations and makes the integration easier to maintain and review.
- **Extensibility:** This approach allows new tools to be added with minimal boilerplate and direct access to CLI features.

### Potential Future Improvements

- **Shared Abstractions:** If more MCP-related code or other integrations are needed, consider extracting shared abstractions (e.g., session management, tool interfaces) into a `common` or `core` module. This would allow for a clean separation and potentially enable a standalone `maestro-mcp` module.
- **Streamable HTTP:** The MCP tool channel uses STDIO only; TapCue opens no listening port for it.
