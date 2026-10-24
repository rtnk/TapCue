# Zależności runtime `maestro-cli` — STEP-05

Migawka sekcji 1 pliku `THIRD-PARTY-NOTICES.md` wygenerowanego przez `:maestro-cli:generateThirdPartyNotices`
(wariant analityczny bez `maestro-android`, który nie jest zależnością runtime CLI; JDK 17). Licencje odczytane z POM
z dziedziczeniem z POM nadrzędnego; żaden artefakt nie wymagał wpisu w `thirdPartyLicenseOverrides`. Pełny plik
(z tekstami licencji i NOTICE z JAR-ów) jest w każdej paczce dystrybucyjnej. Baseline: `runtime-dependencies-v2.11.0.md`.

## 1. Libraries in `lib/`

TapCue modules (Apache-2.0, `LICENSE`): `maestro-cli-2.11.0.jar`, `maestro-client.jar`, `maestro-ios-driver.jar`, `maestro-ios.jar`, `maestro-orchestra-models.jar`, `maestro-orchestra.jar`, `maestro-utils.jar`.

Third-party libraries: 134.

| Library (Maven coordinates) | File | License |
|---|---|---|
| `com.fasterxml.jackson.core:jackson-annotations:2.17.1` | `jackson-annotations-2.17.1.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.jackson.core:jackson-core:2.17.1` | `jackson-core-2.17.1.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.jackson.core:jackson-databind:2.17.1` | `jackson-databind-2.17.1.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1` | `jackson-dataformat-xml-2.17.1.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1` | `jackson-dataformat-yaml-2.17.1.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1` | `jackson-datatype-jsr310-2.17.1.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1` | `jackson-module-kotlin-2.17.1.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.fasterxml.woodstox:woodstox-core:6.6.2` | `woodstox-core-6.6.2.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.github.ajalt.colormath:colormath-jvm:3.6.0` | `colormath-jvm.jar` | The MIT License (https://opensource.org/licenses/MIT) |
| `com.github.ajalt.mordant:mordant-core-jvm:3.0.2` | `mordant-jvm.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `com.github.ajalt.mordant:mordant-jvm-ffm-jvm:3.0.2` | `mordant-jvm-ffm-jvm.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `com.github.ajalt.mordant:mordant-jvm-graal-ffi-jvm:3.0.2` | `mordant-jvm-graal-ffi-jvm.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `com.github.ajalt.mordant:mordant-jvm-jna-jvm:3.0.2` | `mordant-jvm-jna-jvm.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `com.github.ajalt.mordant:mordant-jvm:3.0.2` | `mordant-omnibus-jvm.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `com.github.curious-odd-man:rgxgen:3.1` | `rgxgen-3.1.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.github.romankh3:image-comparison:4.4.0` | `image-comparison-4.4.0.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.android:annotations:4.1.1.4` | `annotations-4.1.1.4.jar` | Apache 2.0 (http://www.apache.org/licenses/LICENSE-2.0) |
| `com.google.api.grpc:proto-google-common-protos:2.9.0` | `proto-google-common-protos-2.9.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.code.findbugs:jsr305:3.0.2` | `jsr305-3.0.2.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.code.gson:gson:2.11.0` | `gson-2.11.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.errorprone:error_prone_annotations:2.27.0` | `error_prone_annotations-2.27.0.jar` | Apache 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.guava:failureaccess:1.0.1` | `failureaccess-1.0.1.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.guava:guava:32.0.1-android` | `guava-32.0.1-android.jar` | Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | `listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.j2objc:j2objc-annotations:2.8` | `j2objc-annotations-2.8.jar` | Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.protobuf:protobuf-java:3.21.9` | `protobuf-java-3.21.9.jar` | BSD-3-Clause (https://opensource.org/licenses/BSD-3-Clause) |
| `com.google.protobuf:protobuf-kotlin:3.21.9` | `protobuf-kotlin-3.21.9.jar` | BSD-3-Clause (https://opensource.org/licenses/BSD-3-Clause) |
| `com.googlecode.libphonenumber:libphonenumber:9.0.17` | `libphonenumber-9.0.17.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.googlecode.plist:dd-plist:1.23` | `dd-plist-1.23.jar` | MIT License (http://opensource.org/licenses/mit) |
| `com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.1` | `kotlin-result-jvm-2.0.1.jar` | ISC License (https://opensource.org/licenses/isc-license.txt) |
| `com.squareup.okhttp3:logging-interceptor:4.12.0` | `logging-interceptor-4.12.0.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.squareup.okhttp3:okhttp:4.12.0` | `okhttp-4.12.0.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.squareup.okio:okio-jvm:3.16.2` | `okio-jvm-3.16.2.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `commons-codec:commons-codec:1.17.0` | `commons-codec-1.17.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `commons-io:commons-io:2.16.1` | `commons-io-2.16.1.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `de.upb.cs.swt:axml:2.1.2` | `axml-2.1.2.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `dev.mobile:dadb:2.0.0` | `dadb-2.0.0.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `info.picocli:picocli:4.6.3` | `picocli-4.6.3.jar` | The Apache Software License, version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.github.oshai:kotlin-logging-jvm:8.0.01` | `kotlin-logging-jvm-8.0.01.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.grpc:grpc-api:1.57.2` | `grpc-api-1.57.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-core:1.50.2` | `grpc-core-1.50.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-kotlin-stub:1.4.1` | `grpc-kotlin-stub-1.4.1.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-netty:1.50.2` | `grpc-netty-1.50.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-okhttp:1.50.2` | `grpc-okhttp-1.50.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-protobuf-lite:1.50.2` | `grpc-protobuf-lite-1.50.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-protobuf:1.50.2` | `grpc-protobuf-1.50.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.grpc:grpc-stub:1.57.2` | `grpc-stub-1.57.2.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `io.ktor:ktor-client-cio-jvm:2.3.13` | `ktor-client-cio-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-client-core-jvm:2.3.13` | `ktor-client-core-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-events-jvm:2.3.13` | `ktor-events-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-http-cio-jvm:2.3.13` | `ktor-http-cio-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-http-jvm:2.3.13` | `ktor-http-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-io-jvm:2.3.13` | `ktor-io-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-network-jvm:2.3.13` | `ktor-network-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-network-tls-jvm:2.3.13` | `ktor-network-tls-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-serialization-jvm:2.3.13` | `ktor-serialization-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-utils-jvm:2.3.13` | `ktor-utils-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-websocket-serialization-jvm:2.3.13` | `ktor-websocket-serialization-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.ktor:ktor-websockets-jvm:2.3.13` | `ktor-websockets-jvm-2.3.13.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.micrometer:micrometer-commons:1.13.4` | `micrometer-commons-1.13.4.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.micrometer:micrometer-core:1.13.4` | `micrometer-core-1.13.4.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.micrometer:micrometer-observation:1.13.4` | `micrometer-observation-1.13.4.jar` | The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `io.modelcontextprotocol:kotlin-sdk-client-jvm:0.11.1` | `kotlin-sdk-client-jvm-0.11.1.jar` | MIT License (https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE) |
| `io.modelcontextprotocol:kotlin-sdk-core-jvm:0.11.1` | `kotlin-sdk-core-jvm-0.11.1.jar` | MIT License (https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE) |
| `io.modelcontextprotocol:kotlin-sdk-jvm:0.11.1` | `kotlin-sdk-jvm-0.11.1.jar` | MIT License (https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE) |
| `io.modelcontextprotocol:kotlin-sdk-server-jvm:0.11.1` | `kotlin-sdk-server-jvm-0.11.1.jar` | MIT License (https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE) |
| `io.netty:netty-buffer:4.1.111.Final` | `netty-buffer-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-codec-http2:4.1.111.Final` | `netty-codec-http2-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-codec-http:4.1.111.Final` | `netty-codec-http-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-codec-socks:4.1.111.Final` | `netty-codec-socks-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-codec:4.1.111.Final` | `netty-codec-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-common:4.1.111.Final` | `netty-common-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-handler-proxy:4.1.111.Final` | `netty-handler-proxy-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-handler:4.1.111.Final` | `netty-handler-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-resolver:4.1.111.Final` | `netty-resolver-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-transport-native-unix-common:4.1.111.Final` | `netty-transport-native-unix-common-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.netty:netty-transport:4.1.111.Final` | `netty-transport-4.1.111.Final.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) |
| `io.perfmark:perfmark-api:0.25.0` | `perfmark-api-0.25.0.jar` | Apache 2.0 (https://opensource.org/licenses/Apache-2.0) |
| `javax.annotation:javax.annotation-api:1.3.2` | `javax.annotation-api-1.3.2.jar` | CDDL + GPLv2 with classpath exception (https://github.com/javaee/javax.annotation/blob/master/LICENSE) |
| `net.datafaker:datafaker:2.5.3` | `datafaker-2.5.3.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `net.dongliu:apk-parser:2.6.10` | `apk-parser-2.6.10.jar` | The BSD 2-Clause License (https://github.com/hsiafan/apk-parser/blob/master/LICENSE.txt) |
| `net.harawata:appdirs:1.2.1` | `appdirs-1.2.1.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `net.java.dev.jna:jna-platform:5.6.0` | `jna-platform-5.6.0.jar` | LGPL, version 2.1 (http://www.gnu.org/licenses/licenses.html); Apache License v2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `net.java.dev.jna:jna:5.14.0` | `jna-5.14.0.jar` | LGPL-2.1-or-later (https://www.gnu.org/licenses/old-licenses/lgpl-2.1); Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.apache.commons:commons-compress:1.21` | `commons-compress-1.21.jar` | Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.apache.logging.log4j:log4j-api:2.25.3` | `log4j-api-2.25.3.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.apache.logging.log4j:log4j-core:2.25.3` | `log4j-core-2.25.3.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.apache.logging.log4j:log4j-layout-template-json:2.25.3` | `log4j-layout-template-json-2.25.3.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3` | `log4j-slf4j2-impl-2.25.3.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.checkerframework:checker-qual:3.33.0` | `checker-qual-3.33.0.jar` | The MIT License (http://opensource.org/licenses/MIT) |
| `org.codehaus.mojo:animal-sniffer-annotations:1.21` | `animal-sniffer-annotations-1.21.jar` | MIT license (http://www.opensource.org/licenses/mit-license.php) |
| `org.codehaus.woodstox:stax2-api:4.2.2` | `stax2-api-4.2.2.jar` | The BSD 2-Clause License (http://www.opensource.org/licenses/bsd-license.php) |
| `org.graalvm.js:js-language:24.2.0` | `js-language-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL); MIT License (http://opensource.org/licenses/MIT) |
| `org.graalvm.polyglot:polyglot:24.2.0` | `polyglot-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.regex:regex:24.2.0` | `regex-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.sdk:collections:24.2.0` | `collections-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.sdk:jniutils:24.2.0` | `jniutils-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.sdk:nativeimage:24.2.0` | `nativeimage-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.sdk:word:24.2.0` | `word-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.shadowed:icu4j:24.2.0` | `icu4j-24.2.0.jar` | Unicode/ICU License (https://raw.githubusercontent.com/unicode-org/icu/main/LICENSE) |
| `org.graalvm.shadowed:xz:24.2.0` | `xz-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.truffle:truffle-api:24.2.0` | `truffle-api-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.truffle:truffle-compiler:24.2.0` | `truffle-compiler-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.graalvm.truffle:truffle-runtime:24.2.0` | `truffle-runtime-24.2.0.jar` | Universal Permissive License, Version 1.0 (http://opensource.org/licenses/UPL) |
| `org.hdrhistogram:HdrHistogram:2.2.2` | `HdrHistogram-2.2.2.jar` | Public Domain, per Creative Commons CC0 (http://creativecommons.org/publicdomain/zero/1.0/); BSD-2-Clause (https://opensource.org/licenses/BSD-2-Clause) |
| `org.jcodec:jcodec-javase:0.2.5` | `jcodec-javase-0.2.5.jar` | FreeBSD (http://jcodec.org/lic.html) |
| `org.jcodec:jcodec:0.2.5` | `jcodec-0.2.5.jar` | FreeBSD (http://jcodec.org/lic.html) |
| `org.jetbrains.kotlin:kotlin-reflect:1.9.20` | `kotlin-reflect-1.9.20.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20` | `kotlin-stdlib-jdk7-1.9.20.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20` | `kotlin-stdlib-jdk8-1.9.20.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.3.10` | `kotlin-stdlib-2.3.10.jar` | Apache-2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.4.0` | `kotlinx-collections-immutable-jvm-0.4.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | `kotlinx-coroutines-core-jvm-1.10.2.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2` | `kotlinx-coroutines-jdk8-1.10.2.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.2` | `kotlinx-coroutines-slf4j-1.10.2.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0` | `kotlinx-html-jvm-0.8.0.jar` | The Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.0` | `kotlinx-io-bytestring-jvm-0.9.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.0` | `kotlinx-io-core-jvm-0.9.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.10.0` | `kotlinx-serialization-core-jvm-1.10.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.10.0` | `kotlinx-serialization-json-jvm-1.10.0.jar` | Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.8.18` | `skiko-awt-runtime-linux-arm64-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.8.18` | `skiko-awt-runtime-linux-x64-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.18` | `skiko-awt-runtime-macos-arm64-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.8.18` | `skiko-awt-runtime-macos-x64-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:0.8.18` | `skiko-awt-runtime-windows-arm64-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.18` | `skiko-awt-runtime-windows-x64-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.skiko:skiko-awt:0.8.18` | `skiko-awt-0.8.18.jar` | The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains:annotations:23.0.0` | `annotations-23.0.0.jar` | The Apache Software License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jline:jansi:3.30.6` | `jansi-3.30.6.jar` | The BSD License (https://opensource.org/licenses/BSD-3-Clause) |
| `org.jline:jline-native:3.30.6` | `jline-native-3.30.6.jar` | The BSD License (https://opensource.org/licenses/BSD-3-Clause) |
| `org.latencyutils:LatencyUtils:2.0.3` | `LatencyUtils-2.0.3.jar` | Public Domain, per Creative Commons CC0 (http://creativecommons.org/publicdomain/zero/1.0/) |
| `org.rauschig:jarchivelib:1.2.0` | `jarchivelib-1.2.0.jar` | Apache 2 (http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.slf4j:slf4j-api:2.0.17` | `slf4j-api-2.0.17.jar` | MIT (https://opensource.org/license/mit) |
| `org.yaml:snakeyaml:2.5` | `snakeyaml-2.5.jar` | Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt) |

