import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.w3c.dom.Element
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.serialization)
}

group = "dev.mobile"

val CLI_VERSION: String by project
version = CLI_VERSION

application {
    applicationName = "tapcue"
    mainClass.set("maestro.cli.AppKt")
    // Suppresses the Java 24+ "restricted method" warning that Jansi/jline trip when loading
    // their native library. Only takes effect for the generated bin/tapcue launcher scripts
    // (i.e. the distribution archives built below). Users invoking the shadow jar with `java -jar` directly will
    // still see the warning on Java 24+ and need to pass --enable-native-access=ALL-UNNAMED
    // themselves; behaviour is otherwise unaffected.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "maestro.cli.AppKt"
    }
    // Include the driver source directly
    from("../maestro-ios-xctest-runner") {
        into("driver/ios")
        // Everything the maestro-driver-ios scheme builds, including the vendored Swift packages: the CLI builds the
        // runner from this source with the local Xcode, offline (D-5: no prebuilt driver is shipped).
        include(
            "maestro-driver-ios/**",
            "maestro-driver-iosUITests/**",
            "maestro-driver-iosTests/**",
            "maestro-driver-ios.xcodeproj/**",
            "MaestroDriverLib/**",
            "Vendor/**",
        )
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = rootDir
}

/** The source that was used to create JvmVersion is here. It was compiled with JDK 1.1
 * on a Windows 32 bit machine using https://www.oracle.com/java/technologies/java-archive-downloads-javase11-downloads.html
 * with a META-INF/MANIFEST.MF of Main-Class: JvmVersion for the jvm-version.jar
 *
 * import java.util.StringTokenizer;
 *
 * class JvmVersion {
 *     public static void main(String[] args) {
 *         try {
 *             String javaVersion = System.getProperty("java.version");
 * 			StringTokenizer tokenizer = new StringTokenizer(javaVersion, ".");
 * 			String[] split = new String[tokenizer.countTokens()];
 * 			int count = 0;
 * 			while (tokenizer.hasMoreTokens()) {
 * 				split[count] = tokenizer.nextToken();
 * 				count++;
 * 			}
 *             if (javaVersion.startsWith("1.")) {
 *                 String version = split[1];
 *                 if (Integer.parseInt(version) >= 1 && Integer.parseInt(version) <= 8) {
 *                     System.out.println(version);
 *                     System.exit(0);
 *                 } else {
 * 					String base = "Expected a JVM version of 1.0 through to 1.8 for legacy JVM versioning. Instead got ";
 *                     String output = base.concat(version);
 *                     System.out.println(output);
 *                     System.exit(1);
 *                 }
 *             } else {
 *                 String version = split[0];
 *                 if (Integer.parseInt(version) >= 9) {
 *                     System.out.println(version);
 *                     System.exit(0);
 *                 } else {
 * 					String base = "Expected a JVM version of 9 or greater for new JVM versioning. Instead got ";
 * 					String output = base.concat(version);
 * 					System.out.println(output);
 *                     System.exit(1);
 *                 }
 *             }
 *         } catch (Exception e) {
 *             System.err.println(e.getMessage());
 *             System.exit(1);
 *         }
 *     }
 * }
 *
 */

fun windowsMinimumJavaText(minimumJavaVersion: String): String = """
set JAVA_VERSION=0
for /f "tokens=*" %%g in ('cmd /c ""%JAVA_EXE%" -classpath "%APP_HOME%\bin\*" JvmVersion"') do (
  set JAVA_VERSION=%%g
)

if %JAVA_VERSION% LSS $minimumJavaVersion (
  echo.
  echo ERROR: Java $minimumJavaVersion or higher is required.
  echo.
  echo Please update Java, then try again.
  echo To check your Java version, run: java -version
  goto fail
)
""".trimIndent().replace("\n", "\r\n")

fun unixMinimumJavaText(minimumJavaVersion: String): String = """
JAVA_VERSION=$( "${'$'}JAVACMD" -classpath "${'$'}APP_HOME"/bin/*.jar JvmVersion )
if [ "${'$'}JAVA_VERSION" -lt $minimumJavaVersion ]; then
  die "ERROR: Java $minimumJavaVersion or higher is required.

Please update Java, then try again.
To check your Java version, run: java -version"
fi
""".trimIndent()

tasks.named<CreateStartScripts>("startScripts") {
    doNotTrackState("classpath uses a JVM wildcard glob that cannot be statted on Windows")
    classpath = files("${layout.buildDirectory.get().asFile}/libs/*")
    doLast {
        val minimumJavaVersion = "17"
        val unixExec = "exec \"\$JAVACMD\" \"$@\""

        val currentUnix = unixScript.readText()
        val replacedUnix = currentUnix.replaceFirst(unixExec,
            unixMinimumJavaText(minimumJavaVersion) + "\n\n" + unixExec)
        unixScript.writeText(replacedUnix)

        val currentWindows = windowsScript.readText()
        val windowsExec = "@rem Execute $applicationName"
        val replacedWindows = currentWindows.replaceFirst(windowsExec,
            windowsMinimumJavaText(minimumJavaVersion) + "\r\n\r\n" + windowsExec)
        windowsScript.writeText(replacedWindows)

        val path = project.projectDir.toPath().resolve("jvm-version.jar")

        copy {
            from(path)
            into(outputDir)
        }
    }
}

dependencies {
    implementation(project(path = ":maestro-utils"))
    annotationProcessor(libs.picocli.codegen)

    implementation(project(":maestro-orchestra"))
    implementation(project(":maestro-client"))
    implementation(project(":maestro-ios"))
    implementation(project(":maestro-ios-driver"))
    implementation(libs.apk.parser)
    implementation(libs.dd.plist)
    implementation(libs.dadb)
    implementation(libs.picocli)
    implementation(libs.jackson.core.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jansi)
    implementation(libs.jansinative)
    implementation(libs.jcodec)
    implementation(libs.jcodec.awt)
    implementation(libs.square.okhttp)
    // Keeps gRPC's netty transport at the version the CLI shipped before the Viewer (ktor-server-netty) was removed.
    implementation(platform(libs.netty.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.jarchivelib)
    implementation(libs.commons.codec)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.html)
    implementation(libs.skiko.macos.arm64)
    implementation(libs.skiko.macos.x64)
    implementation(libs.skiko.linux.arm64)
    implementation(libs.skiko.linux.x64)
    implementation(libs.skiko.windows.arm64)
    implementation(libs.skiko.windows.x64)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.2.0")
    implementation(libs.mcp.kotlin.sdk) {
        exclude(group = "org.slf4j", module = "slf4j-simple")
        exclude(group = "io.ktor")
    }
    implementation(libs.logging.sl4j)
    implementation(libs.logging.api)
    implementation(libs.logging.layout.template)
    implementation(libs.log4j.core)
    implementation(libs.mordant)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.google.truth)
    testImplementation(libs.system.stubs.jupiter)
    testImplementation(libs.square.mock.server)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // CliJarContentsTest inspects the real JAR: simulator-server and the Viewer frontend must never
    // be packaged (docs/LICENSE-AUDIT.md L-01).
    val cliJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(cliJar)
    systemProperty("maestro.cli.jar", cliJar.get().asFile.absolutePath)
    // StartScriptsTest reads the generated bin/tapcue and bin/tapcue.bat (N-35, D-7).
    val startScripts = tasks.named<CreateStartScripts>("startScripts").map { it.outputDir!! }
    inputs.dir(startScripts)
    systemProperty("tapcue.start.scripts", startScripts.get().absolutePath)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjdk-release=17")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.create("createProperties") {
    dependsOn("processResources")

    doLast {
        File("$buildDir/resources/main/version.properties").writer().use { w ->
            val p = Properties()
            p["version"] = CLI_VERSION
            p.store(w, null)
        }
    }
}

tasks.register<Copy>("createTestResources") {
    from("../maestro-ios-xctest-runner") {
        into("driver/ios")
        include(
            "maestro-driver-ios/**",
            "maestro-driver-iosUITests/**",
            "maestro-driver-iosTests/**",
            "maestro-driver-ios.xcodeproj/**",
            "MaestroDriverLib/**",
            "Vendor/**",
        )
    }
    into(layout.buildDirectory.dir("resources/test"))
}

tasks.named("classes") {
    dependsOn("createTestResources")
    dependsOn("createProperties")
}

// Distribution (STEP-05, decisions D-1, D-5 and D-7 in docs/ROADMAP-0.1.md). Every archive holds bin/tapcue, lib/*.jar,
// LICENSE, NOTICE, THIRD-PARTY-NOTICES.md and README.md (src/dist). The release artifact is
// tapcue-<version>-macos-arm64.zip; scripts/verify-dist.sh checks it for forbidden content.

/**
 * Licenses for runtime dependencies whose POM (and parent POMs) declare none, keyed by `group:artifact`.
 * The notices task fails on any other dependency without a license, so a new one cannot slip in unlisted.
 */
val thirdPartyLicenseOverrides = mapOf<String, String>(
)

val generateThirdPartyNotices by tasks.registering(ThirdPartyNoticesTask::class) {
    description = "Writes THIRD-PARTY-NOTICES.md for the runtime classpath (licenses from POMs, texts and NOTICE files from JARs)."
    runtimeClasspath.from(configurations.runtimeClasspath)
    applicationJarName.set(tasks.named<Jar>("jar").flatMap { it.archiveFileName })
    licenseOverrides.set(thirdPartyLicenseOverrides)
    supplementalNotices.set(rootProject.file("docs/audit/notices-supplement.md"))
    outputFile.set(layout.buildDirectory.file("generated/notices/THIRD-PARTY-NOTICES.md"))
}

distributions {
    named("main") {
        distributionBaseName.set("tapcue")
        contents {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("NOTICE"))
            from(generateThirdPartyNotices)
        }
    }
    // The macOS arm64 package leaves out the Skia runtimes of other platforms and the Windows launcher.
    register("macosArm64") {
        distributionBaseName.set("tapcue")
        contents {
            with(distributions["main"].contents)
            exclude(
                "**/skiko-awt-runtime-linux-*.jar",
                "**/skiko-awt-runtime-windows-*.jar",
                "**/skiko-awt-runtime-macos-x64-*.jar",
                "**/*.bat",
            )
        }
    }
}

tasks.withType<AbstractArchiveTask>().matching { it.name.endsWith("DistZip") || it.name.endsWith("DistTar") || it.name in setOf("distZip", "distTar") }.configureEach {
    // The same bytes for the same inputs, so SHA256SUMS can be reproduced.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<Zip>("distZip") {
    archiveFileName.set("tapcue-$CLI_VERSION.zip")
}

tasks.named<Tar>("distTar") {
    archiveFileName.set("tapcue-$CLI_VERSION.tar")
}

tasks.named<Zip>("macosArm64DistZip") {
    archiveFileName.set("tapcue-$CLI_VERSION-macos-arm64.zip")
}

tasks.named<Tar>("macosArm64DistTar") {
    enabled = false
}

/** SHA256SUMS next to the release archive, in the format `shasum -a 256 -c SHA256SUMS` reads. */
val macosArm64DistSha256 by tasks.registering {
    description = "Writes build/distributions/SHA256SUMS for the macOS arm64 archive."
    val archive = tasks.named<Zip>("macosArm64DistZip").flatMap { it.archiveFile }
    val sums = layout.buildDirectory.file("distributions/SHA256SUMS")
    inputs.file(archive)
    outputs.file(sums)
    doLast {
        val file = archive.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        sums.get().asFile.writeText("$hex  ${file.name}\n")
    }
}

tasks.named("macosArm64DistZip") {
    finalizedBy(macosArm64DistSha256)
}

tasks.shadowJar {
    setProperty("zip64", true)
}


/**
 * Writes THIRD-PARTY-NOTICES.md: one row per runtime JAR with the license declared in its POM (following parent POMs),
 * then every license and NOTICE text bundled in those JARs (Apache-2.0 section 4(d)), then the hand-written supplement
 * (driver APKs, bundled tools). Not configuration-cache compatible: it resolves POMs while running.
 */
abstract class ThirdPartyNoticesTask : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val applicationJarName: Property<String>

    @get:Input
    abstract val licenseOverrides: MapProperty<String, String>

    @get:InputFile
    abstract val supplementalNotices: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private data class License(val name: String, val url: String?)

    @TaskAction
    fun generate() {
        val artifacts = project.configurations.getByName("runtimeClasspath").incoming.artifacts.artifacts
        val external = artifacts.mapNotNull { artifact ->
            (artifact.id.componentIdentifier as? ModuleComponentIdentifier)?.let { it to artifact.file }
        }.sortedBy { (id, _) -> "${id.group}:${id.module}:${id.version}" }
        val own = (artifacts.filter { it.id.componentIdentifier is ProjectComponentIdentifier }.map { it.file.name } +
            applicationJarName.get()).sorted()

        val overrides = licenseOverrides.get()
        val missing = mutableListOf<String>()
        val rows = external.map { (id, file) ->
            val key = "${id.group}:${id.module}"
            val licenses = overrides[key]?.let { listOf(License(it, null)) } ?: pomLicenses(id.group, id.module, id.version)
            if (licenses.isEmpty()) missing += "$key:${id.version}"
            Triple("$key:${id.version}", file.name, licenses)
        }
        check(missing.isEmpty()) {
            "No license in the POM of: ${missing.joinToString()}. Check the artifact and add it to thirdPartyLicenseOverrides in maestro-cli/build.gradle.kts."
        }

        // Unique bundled texts, each with the JARs that carry it.
        val texts = linkedMapOf<String, Pair<String, MutableList<String>>>()
        external.forEach { (id, file) ->
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && isLegalText(it.name) }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        val text = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8).trimEnd()
                        if (text.isNotBlank()) {
                            val hash = sha256(text)
                            texts.getOrPut(hash) { text to mutableListOf() }.second += "`${id.module}:${id.version}` (`${entry.name}`)"
                        }
                    }
            }
        }

        val out = StringBuilder()
        out.appendLine("# Third-party notices — TapCue")
        out.appendLine()
        out.appendLine("TapCue is based on Maestro (Apache-2.0), not affiliated with mobile.dev. TapCue itself is licensed under the")
        out.appendLine("Apache License 2.0 (`LICENSE`); see `NOTICE` for attribution. This file lists every third-party library in `lib/`,")
        out.appendLine("the license its Maven POM declares, and reproduces the license and NOTICE texts bundled in those libraries.")
        out.appendLine("Generated by the `:maestro-cli:generateThirdPartyNotices` Gradle task.")
        out.appendLine()
        out.appendLine("## 1. Libraries in `lib/`")
        out.appendLine()
        out.appendLine("TapCue modules (Apache-2.0, `LICENSE`): ${own.joinToString(", ") { "`$it`" }}.")
        out.appendLine()
        out.appendLine("Third-party libraries: ${rows.size}.")
        out.appendLine()
        out.appendLine("| Library (Maven coordinates) | File | License |")
        out.appendLine("|---|---|---|")
        rows.forEach { (gav, fileName, licenses) ->
            val license = licenses.joinToString("; ") { l -> if (l.url.isNullOrBlank()) l.name else "${l.name} (${l.url})" }
            out.appendLine("| `$gav` | `$fileName` | ${license.replace("|", "\\|")} |")
        }
        out.appendLine()
        out.appendLine("## 2. License and NOTICE texts bundled in the libraries")
        out.appendLine()
        texts.values.forEach { (text, carriers) ->
            out.appendLine("### From ${carriers.joinToString(", ")}")
            out.appendLine()
            out.appendLine("````text")
            out.appendLine(text)
            out.appendLine("````")
            out.appendLine()
        }
        out.appendLine("## 3. Other components")
        out.appendLine()
        out.append(supplementalNotices.get().asFile.readText().trimEnd())
        out.appendLine()

        outputFile.get().asFile.apply { parentFile.mkdirs() }.writeText(out.toString())
    }

    private fun isLegalText(path: String): Boolean {
        if (path.count { it == '/' } > 2 || path.endsWith(".class")) return false
        val name = path.substringAfterLast('/')
        return Regex("(?i)(licen[cs]e|notice|copying|copyright)([._-].*)?").matches(name)
    }

    private fun pomLicenses(group: String, module: String, version: String, depth: Int = 0): List<License> {
        val pom = runCatching {
            project.configurations.detachedConfiguration(project.dependencies.create("$group:$module:$version@pom"))
                .apply { isTransitive = false }
                .singleFile
        }.getOrNull() ?: return emptyList()
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom).documentElement
        val licenses = root.child("licenses")?.children("license").orEmpty().mapNotNull { license ->
            license.child("name")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { License(it, license.child("url")?.textContent?.trim()) }
        }
        if (licenses.isNotEmpty() || depth >= 5) return licenses
        val parent = root.child("parent") ?: return emptyList()
        val parentGroup = parent.child("groupId")?.textContent?.trim() ?: return emptyList()
        val parentModule = parent.child("artifactId")?.textContent?.trim() ?: return emptyList()
        val parentVersion = parent.child("version")?.textContent?.trim() ?: return emptyList()
        return pomLicenses(parentGroup, parentModule, parentVersion, depth + 1)
    }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == name }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
