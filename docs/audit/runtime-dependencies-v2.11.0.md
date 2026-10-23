# Zależności runtime `maestro-cli` — baseline `v2.11.0`

Wygenerowane w STEP-00 z `configurations.runtimeClasspath` modułu `:maestro-cli` (wariant analityczny: bez modułu `maestro-android`, który nie jest zależnością runtime CLI — APK-i są zasobami `maestro-client`). Licencje odczytane z POM (z dziedziczeniem z POM nadrzędnego). Kolumna *natywne* — liczba bibliotek `.so/.dylib/.dll/.jnilib` lub binariów `selenium-manager` wewnątrz JAR. Wartość `?` = brak licencji w POM — do ręcznej weryfikacji w STEP-05.

Liczba artefaktów zewnętrznych: 186.

| Artefakt (GAV) | Licencja wg POM | natywne | przykłady |
|---|---|---|---|
| `com.fasterxml.jackson.core:jackson-annotations:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.jackson.core:jackson-core:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.jackson.core:jackson-databind:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.fasterxml.woodstox:woodstox-core:6.6.2` | The Apache License, Version 2.0 | 0 | —  |
| `com.github.ajalt.clikt:clikt-jvm:4.2.2` | Apache-2.0 | 0 | —  |
| `com.github.ajalt.colormath:colormath-jvm:3.6.0` | The MIT License | 0 | —  |
| `com.github.ajalt.mordant:mordant-core-jvm:3.0.2` | Apache-2.0 | 0 | —  |
| `com.github.ajalt.mordant:mordant-jvm-ffm-jvm:3.0.2` | Apache-2.0 | 0 | —  |
| `com.github.ajalt.mordant:mordant-jvm-graal-ffi-jvm:3.0.2` | Apache-2.0 | 0 | —  |
| `com.github.ajalt.mordant:mordant-jvm-jna-jvm:3.0.2` | Apache-2.0 | 0 | —  |
| `com.github.ajalt.mordant:mordant-jvm:3.0.2` | Apache-2.0 | 0 | —  |
| `com.github.curious-odd-man:rgxgen:3.1` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.github.romankh3:image-comparison:4.4.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.google.android:annotations:4.1.1.4` | Apache 2.0 | 0 | —  |
| `com.google.api.grpc:proto-google-common-protos:2.9.0` | Apache-2.0 | 0 | —  |
| `com.google.auto.service:auto-service-annotations:1.1.1` | Apache 2.0 | 0 | —  |
| `com.google.code.findbugs:jsr305:3.0.2` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.google.code.gson:gson:2.11.0` | Apache-2.0 | 0 | —  |
| `com.google.errorprone:error_prone_annotations:2.41.0` | Apache 2.0 | 0 | —  |
| `com.google.guava:failureaccess:1.0.3` | Apache License, Version 2.0 | 0 | —  |
| `com.google.guava:guava:33.5.0-jre` | Apache License, Version 2.0 | 0 | —  |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.google.j2objc:j2objc-annotations:3.1` | Apache License, Version 2.0 | 0 | —  |
| `com.google.protobuf:protobuf-java:3.21.9` | BSD-3-Clause | 0 | —  |
| `com.google.protobuf:protobuf-kotlin:3.21.9` | BSD-3-Clause | 0 | —  |
| `com.googlecode.libphonenumber:libphonenumber:9.0.17` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.googlecode.plist:dd-plist:1.23` | MIT License | 0 | —  |
| `com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.1` | ISC License | 0 | —  |
| `com.posthog:posthog-server:1.0.3` | MIT | 0 | —  |
| `com.posthog:posthog:3.23.1` | MIT | 0 | —  |
| `com.squareup.okhttp3:logging-interceptor:4.12.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.squareup.okhttp3:okhttp:4.12.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.squareup.okio:okio-jvm:3.16.2` | The Apache Software License, Version 2.0 | 0 | —  |
| `com.typesafe:config:1.4.3` | Apache-2.0 | 0 | —  |
| `commons-codec:commons-codec:1.17.0` | Apache-2.0 | 0 | —  |
| `commons-io:commons-io:2.16.1` | Apache-2.0 | 0 | —  |
| `de.upb.cs.swt:axml:2.1.2` | The Apache Software License, Version 2.0 | 0 | —  |
| `dev.mobile:dadb:2.0.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `info.picocli:picocli:4.6.3` | The Apache Software License, version 2.0 | 0 | —  |
| `io.github.oshai:kotlin-logging-jvm:8.0.01` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.grpc:grpc-api:1.57.2` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-core:1.50.2` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-kotlin-stub:1.4.1` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-netty:1.50.2` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-okhttp:1.50.2` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-protobuf-lite:1.50.2` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-protobuf:1.50.2` | Apache 2.0 | 0 | —  |
| `io.grpc:grpc-stub:1.57.2` | Apache 2.0 | 0 | —  |
| `io.ktor:ktor-client-cio-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-client-content-negotiation-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-client-core-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-events-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-http-cio-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-http-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-io-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-network-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-network-tls-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-serialization-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-serialization-kotlinx-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-server-core-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-server-cors-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-server-host-common-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-server-netty-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-server-status-pages-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-utils-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-websocket-serialization-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.ktor:ktor-websockets-jvm:2.3.13` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.micrometer:micrometer-commons:1.13.4` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.micrometer:micrometer-core:1.13.4` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.micrometer:micrometer-observation:1.13.4` | The Apache Software License, Version 2.0 | 0 | —  |
| `io.modelcontextprotocol:kotlin-sdk-client-jvm:0.11.1` | MIT License | 0 | —  |
| `io.modelcontextprotocol:kotlin-sdk-core-jvm:0.11.1` | MIT License | 0 | —  |
| `io.modelcontextprotocol:kotlin-sdk-jvm:0.11.1` | MIT License | 0 | —  |
| `io.modelcontextprotocol:kotlin-sdk-server-jvm:0.11.1` | MIT License | 0 | —  |
| `io.netty:netty-buffer:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-codec-http2:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-codec-http:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-codec-socks:4.1.79.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-codec:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-common:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-handler-proxy:4.1.79.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-handler:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-resolver:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-transport-classes-epoll:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-transport-classes-kqueue:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-transport-native-epoll:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-transport-native-kqueue:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-transport-native-unix-common:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.netty:netty-transport:4.1.111.Final` | Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-api:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-common:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-context:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-exporter-logging:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk-common:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk-logs:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk-metrics:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk-trace:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.opentelemetry:opentelemetry-sdk:1.60.1` | The Apache License, Version 2.0 | 0 | —  |
| `io.perfmark:perfmark-api:0.25.0` | Apache 2.0 | 0 | —  |
| `javax.annotation:javax.annotation-api:1.3.2` | CDDL + GPLv2 with classpath exception | 0 | —  |
| `net.bytebuddy:byte-buddy:1.18.8-jdk5` | Apache License, Version 2.0 | 0 | —  |
| `net.datafaker:datafaker:2.5.3` | The Apache Software License, Version 2.0 | 0 | —  |
| `net.dongliu:apk-parser:2.6.10` | The BSD 2-Clause License | 0 | —  |
| `net.harawata:appdirs:1.2.1` | The Apache License, Version 2.0 | 0 | —  |
| `net.java.dev.jna:jna-platform:5.6.0` | Apache License v2.0; LGPL, version 2.1 | 0 | —  |
| `net.java.dev.jna:jna:5.14.0` | Apache-2.0; LGPL-2.1-or-later | 24 | jnidispatch.dll, libjnidispatch.jnilib, libjnidispatch.jnilib… |
| `org.apache.commons:commons-compress:1.21` | Apache License, Version 2.0 | 0 | —  |
| `org.apache.logging.log4j:log4j-api:2.25.3` | Apache-2.0 | 0 | —  |
| `org.apache.logging.log4j:log4j-core:2.25.3` | Apache-2.0 | 0 | —  |
| `org.apache.logging.log4j:log4j-layout-template-json:2.25.3` | Apache-2.0 | 0 | —  |
| `org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3` | Apache-2.0 | 0 | —  |
| `org.codehaus.mojo:animal-sniffer-annotations:1.21` | MIT license | 0 | —  |
| `org.codehaus.woodstox:stax2-api:4.2.2` | The BSD 2-Clause License | 0 | —  |
| `org.eclipse.jetty.alpn:alpn-api:1.1.3.v20160715` | Apache Software License - Version 2.0; Eclipse Public License - Version 1.0 | 0 | —  |
| `org.fusesource.jansi:jansi:2.4.1` | Apache License, Version 2.0 | 15 | libjansi.so, libjansi.so, libjansi.so… |
| `org.graalvm.js:js-community:24.2.0` | MIT License; Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.js:js-language:24.2.0` | MIT License; Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.js:js-scriptengine:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.polyglot:polyglot:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.regex:regex:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.sdk:collections:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.sdk:jniutils:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.sdk:nativebridge:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.sdk:nativeimage:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.sdk:word:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.shadowed:icu4j:24.2.0` | Unicode/ICU License | 0 | —  |
| `org.graalvm.shadowed:xz:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.truffle:truffle-api:24.2.0` | Universal Permissive License, Version 1.0 | 5 | libtruffleattach.so, libtruffleattach.so, libtruffleattach.dylib… |
| `org.graalvm.truffle:truffle-compiler:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.graalvm.truffle:truffle-enterprise:24.2.0` | GraalVM Free Terms and Conditions (GFTC) including License for Early Adopter Versions | 0 | —  |
| `org.graalvm.truffle:truffle-runtime:24.2.0` | Universal Permissive License, Version 1.0 | 0 | —  |
| `org.hdrhistogram:HdrHistogram:2.2.2` | BSD-2-Clause; Public Domain, per Creative Commons CC0 | 0 | —  |
| `org.jcodec:jcodec-javase:0.2.5` | FreeBSD | 0 | —  |
| `org.jcodec:jcodec:0.2.5` | FreeBSD | 0 | —  |
| `org.jetbrains.kotlin:kotlin-reflect:1.9.20` | The Apache License, Version 2.0 | 0 | —  |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20` | The Apache License, Version 2.0 | 0 | —  |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20` | The Apache License, Version 2.0 | 0 | —  |
| `org.jetbrains.kotlin:kotlin-stdlib:2.3.10` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.4.0` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.2` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0` | The Apache License, Version 2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.0` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.0` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.10.0` | Apache-2.0 | 0 | —  |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.10.0` | Apache-2.0 | 0 | —  |
| `org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.8.18` | The Apache License, Version 2.0 | 1 | libskiko-linux-arm64.so |
| `org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.8.18` | The Apache License, Version 2.0 | 1 | libskiko-linux-x64.so |
| `org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.18` | The Apache License, Version 2.0 | 2 | libskiko-macos-arm64.dylib, libskiko-macos-x64.dylib |
| `org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.8.18` | The Apache License, Version 2.0 | 2 | libskiko-macos-arm64.dylib, libskiko-macos-x64.dylib |
| `org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:0.8.18` | The Apache License, Version 2.0 | 1 | skiko-windows-arm64.dll |
| `org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.18` | The Apache License, Version 2.0 | 1 | skiko-windows-x64.dll |
| `org.jetbrains.skiko:skiko-awt:0.8.18` | The Apache License, Version 2.0 | 0 | —  |
| `org.jetbrains:annotations:23.0.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.jline:jansi:3.30.6` | The BSD License | 15 | libjlinenative.so, libjlinenative.so, libjlinenative.so… |
| `org.jline:jline-native:3.30.6` | The BSD License | 15 | libjlinenative.so, libjlinenative.so, libjlinenative.so… |
| `org.jspecify:jspecify:1.0.0` | The Apache License, Version 2.0 | 0 | —  |
| `org.latencyutils:LatencyUtils:2.0.3` | Public Domain, per Creative Commons CC0 | 0 | —  |
| `org.rauschig:jarchivelib:1.2.0` | Apache 2 | 0 | —  |
| `org.seleniumhq.selenium:selenium-api:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-chrome-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-chromium-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-devtools-v145:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-devtools-v146:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-devtools-v147:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-edge-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-firefox-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-http:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-ie-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-java:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-json:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-manager:4.43.0` | The Apache Software License, Version 2.0 | 3 | selenium-manager, selenium-manager, selenium-manager.exe |
| `org.seleniumhq.selenium:selenium-os:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-remote-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-safari-driver:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.seleniumhq.selenium:selenium-support:4.43.0` | The Apache Software License, Version 2.0 | 0 | —  |
| `org.slf4j:slf4j-api:2.0.17` | MIT | 0 | —  |
| `org.yaml:snakeyaml:2.5` | Apache License, Version 2.0 | 0 | —  |
