package maestro.cli.mcp

import device.SimctlIOSDevice
import ios.xctest.XCTestIOSDevice
import maestro.Maestro
import maestro.MaestroException
import maestro.cli.CliError
import maestro.cli.report.TestDebugReporter
import maestro.android.AndroidDeviceConnection
import maestro.device.DeviceService
import maestro.device.Device
import maestro.device.Platform
import maestro.drivers.AndroidDriver
import maestro.drivers.IOSDriver
import maestro.utils.CliInsights
import maestro.utils.TempFileHandler
import util.IOSDeviceType
import util.XCRunnerCLIUtils
import xcuitest.XCTestClient
import xcuitest.XCTestDriverClient
import xcuitest.installer.Context
import xcuitest.installer.LocalXCTestInstaller
import xcuitest.installer.LocalXCTestInstaller.IOSDriverConfig
import java.util.concurrent.ConcurrentHashMap

internal class McpMaestroSessionManager : AutoCloseable {
    private val sessions = ConcurrentHashMap<String, McpMaestroSession>()

    fun <T> withSession(
        deviceId: String,
        block: (McpMaestroSession) -> T,
    ): T {
        val session = sessions.computeIfAbsent(deviceId) { createSession(deviceId) }
        return block(session)
    }

    override fun close() {
        sessions.values.forEach { session ->
            runCatching { session.close() }
        }
        sessions.clear()
    }

    private fun createSession(deviceId: String): McpMaestroSession {
        if (deviceId == WEB_DEVICE_ID) {
            throw MaestroException.WebPlatformNotSupported()
        }

        val device = DeviceService.listConnectedDevices()
            .find { it.instanceId.equals(deviceId, ignoreCase = true) }
            ?: throw CliError("Device with id $deviceId is not connected")
        if (!isSupported(device)) {
            throw UnsupportedOperationException(
                "Device ${device.instanceId} (${device.platform}/${device.deviceType}) is not supported by the MCP server"
            )
        }

        return when (device.platform) {
            Platform.ANDROID -> createAndroidSession(device)
            Platform.IOS -> createIosSession(device)
            Platform.WEB -> throw MaestroException.WebPlatformNotSupported()
        }
    }

    // Android emulators and devices, iOS simulators.
    private fun isSupported(device: Device.Connected): Boolean = when (device.platform) {
        Platform.ANDROID -> device.deviceType == Device.DeviceType.EMULATOR || device.deviceType == Device.DeviceType.REAL
        Platform.IOS -> device.deviceType == Device.DeviceType.SIMULATOR
        Platform.WEB -> false
    }

    private fun createAndroidSession(device: Device.Connected): McpMaestroSession {
        val connection = AndroidDeviceConnection.byId(device.instanceId)
            ?: error("Unable to find device with id ${device.instanceId}")
        val driver = AndroidDriver(connection, device.instanceId, true)
        return McpMaestroSession(
            maestro = Maestro.android(driver),
            platform = "android",
            deviceId = device.instanceId,
        )
    }

    private fun createIosSession(device: Device.Connected): McpMaestroSession {
        val driver = createIOSDriver(device.instanceId, device.deviceType)
        return McpMaestroSession(
            maestro = Maestro.ios(driver, openDriver = true),
            platform = "ios",
            deviceId = device.instanceId,
        )
    }

    private fun createIOSDriver(
        deviceId: String,
        deviceType: Device.DeviceType,
    ): IOSDriver {
        require(deviceType == Device.DeviceType.SIMULATOR) {
            "Unsupported device type $deviceType for iOS platform"
        }

        val iOSDriverConfig = IOSDriverConfig(
            prebuiltRunner = false,
            sourceDirectory = "driver-iPhoneSimulator",
            context = Context.CLI,
            snapshotKeyHonorModalViews = null,
        )

        val tempFileHandler = TempFileHandler()
        val deviceController = SimctlIOSDevice(
            deviceId = deviceId,
            tempFileHandler = tempFileHandler,
        )

        val xcTestInstaller = LocalXCTestInstaller(
            deviceId = deviceId,
            host = DEFAULT_XCTEST_HOST,
            defaultPort = DEFAULT_XCTEST_PORT,
            reinstallDriver = true,
            deviceType = IOSDeviceType.SIMULATOR,
            iOSDriverConfig = iOSDriverConfig,
            deviceController = deviceController,
            tempFileHandler = tempFileHandler,
            logsDir = TestDebugReporter.getDebugOutputPath().toFile(),
        )

        val xcTestDevice = XCTestIOSDevice(
            deviceId = deviceId,
            client = XCTestDriverClient(
                installer = xcTestInstaller,
                client = XCTestClient(DEFAULT_XCTEST_HOST, DEFAULT_XCTEST_PORT),
                reinstallDriver = true,
            ),
            getInstalledApps = { XCRunnerCLIUtils(tempFileHandler).listApps(deviceId) },
        )

        return IOSDriver(
            iosDevice = ios.LocalIOSDevice(
                deviceId = deviceId,
                xcTestDevice = xcTestDevice,
                deviceController = deviceController,
                insights = CliInsights,
            ),
            insights = CliInsights,
        )
    }

    data class McpMaestroSession(
        val maestro: Maestro,
        val platform: String,
        val deviceId: String,
    ) {
        fun close() {
            maestro.close()
        }
    }

    private companion object {
        private const val DEFAULT_XCTEST_HOST = "127.0.0.1"
        private const val DEFAULT_XCTEST_PORT = 22087
        private const val WEB_DEVICE_ID = "chromium"
    }
}
