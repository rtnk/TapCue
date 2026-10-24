/*
 *
 *  Copyright (c) 2022 mobile.dev inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package maestro.cli

import maestro.MaestroException
import maestro.cli.command.BugReportCommand
import maestro.cli.command.ChatCommand
import maestro.cli.command.CheckSyntaxCommand
import maestro.cli.command.DriverCommand
import maestro.cli.command.ListDevicesCommand
import maestro.cli.command.McpCommand
import maestro.cli.command.PrintHierarchyCommand
import maestro.cli.command.QueryCommand
import maestro.cli.command.RecordCommand
import maestro.cli.command.StartDeviceCommand
import maestro.cli.command.TestCommand
import maestro.cli.mcp.claimMcpStdout
import maestro.debuglog.DebugLogStore
import maestro.debuglog.LogConfig
import maestro.utils.TapCueDirs
import picocli.AutoComplete.GenerateCompletion
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.*
import kotlin.system.exitProcess

/** Shown by `--help` and `--version` (decision D-1 in docs/ROADMAP-0.1.md). */
internal const val ORIGIN_NOTICE = "based on Maestro (Apache-2.0), not affiliated with mobile.dev"

@Command(
    name = TapCueDirs.NAME,
    header = ["TapCue - local mobile UI test automation, $ORIGIN_NOTICE", ""],
    subcommands = [
        TestCommand::class,
        RecordCommand::class,
        PrintHierarchyCommand::class,
        QueryCommand::class,
        BugReportCommand::class,
        StartDeviceCommand::class,
        ListDevicesCommand::class,
        GenerateCompletion::class,
        ChatCommand::class,
        CheckSyntaxCommand::class,
        DriverCommand::class,
        McpCommand::class,
    ]
)
class App {
    @CommandLine.Mixin
    var disableANSIMixin: DisableAnsiMixin? = null

    @CommandLine.Mixin
    var showHelpMixin: ShowHelpMixin? = null

    @Option(names = ["-v", "--version"], versionHelp = true, description = ["Display CLI version"])
    var requestedVersion: Boolean? = false

    @Option(names = ["-p", "--platform"], description = ["(Optional) Select a platform to run on"])
    var platform: String? = null

    @Option(names = ["--host"], hidden = true)
    var host: String? = null

    @Option(names = ["--port"], hidden = true)
    var port: Int? = null

    @Option(names = ["--driver-host-port"], hidden = true)
    var driverHostPort: Int? = null

    @Option(
        names = ["--device", "--udid"],
        description = ["(Optional) Device ID to run on explicitly, can be a comma separated list of IDs: --device \"Emulator_1,Emulator_2\" "],
    )
    var deviceId: String? = null

    @Option(names = ["--verbose"], description = ["Enable verbose logging"])
    var verbose: Boolean = false
}

/** `tapcue <version>` followed by [ORIGIN_NOTICE]. */
internal fun versionText(): String {
    val props = App::class.java.classLoader.getResourceAsStream("version.properties").use {
        Properties().apply { load(it) }
    }

    return "${TapCueDirs.NAME} ${props["version"]}\n$ORIGIN_NOTICE"
}

fun main(args: Array<String>) {
    // Must run before any other init: dependency banners and kotlin-logging's
    // first-load message will otherwise land on the MCP JSON-RPC channel and
    // break the handshake for strict clients like Claude Desktop.
    if (args.firstOrNull() == "mcp") claimMcpStdout()

    // Establish a consistent logging baseline for every command before any init
    // logging fires. Without this, commands that don't call TestDebugReporter.install()
    // fall back to log4j's default console-at-ERROR config and silently ignore the
    // global --verbose flag. Console-only here (no file) so lightweight commands don't
    // litter the state dir; commands that want a maestro.log still opt in via install().
    LogConfig.configure(logFileName = null, printToConsole = args.contains("--verbose"))

    // Disable icon in Mac dock
    // https://stackoverflow.com/a/17544259
    System.setProperty("apple.awt.UIElement", "true")

    Dependencies.install()

    val commandLine = createCommandLine()

    val exitCode = commandLine
        .execute(*args)

    DebugLogStore.finalizeRun()

    if (commandLine.isVersionHelpRequested) {
        println(versionText())
        exitProcess(0)
    }

    exitProcess(exitCode)
}

/**
 * The CLI's command tree and its error handling. A failing command is reported on stderr only;
 * nothing about it leaves the machine.
 */
internal fun createCommandLine(): CommandLine {
    val commandLine = CommandLine(App())
        .setUsageHelpWidth(160)
        .setCaseInsensitiveEnumValuesAllowed(true)
        .setExecutionStrategy(DisableAnsiMixin::executionStrategy)
        .setExecutionExceptionHandler { ex, cmd, _ ->
            // make errors red
            println()
            cmd.colorScheme = CommandLine.Help.ColorScheme.Builder()
                .errors(CommandLine.Help.Ansi.Style.fg_red)
                .build()

            cmd.err.println(
                cmd.colorScheme.errorText(ex.message.orEmpty())
            )

            if (
                ex !is CliError && ex !is MaestroException.WebPlatformNotSupported
                && ex !is MaestroException.MissingAppleTeamId && ex !is MaestroException.IOSDeviceDriverSetupException
            ) {
                cmd.err.println("\nThe stack trace was:")
                cmd.err.println(ex.stackTraceToString())
            }

            1
        }

    val generateCompletionCommand = commandLine.subcommands["generate-completion"]
    generateCompletionCommand?.commandSpec?.usageMessage()?.hidden(true)

    return commandLine
}
