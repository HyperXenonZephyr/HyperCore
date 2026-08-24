plugins {
    `java-library`
}

val lwjglVersion = "3.3.3"
val nativeClassifier = when {
    System.getProperty("os.name").lowercase().contains("win") && System.getProperty("os.arch").lowercase().contains("arm") -> "natives-windows-arm64"
    System.getProperty("os.name").lowercase().contains("win") -> "natives-windows"
    System.getProperty("os.name").lowercase().contains("mac") && System.getProperty("os.arch").lowercase().contains("arm") -> "natives-macos-arm64"
    System.getProperty("os.name").lowercase().contains("mac") -> "natives-macos"
    System.getProperty("os.arch").lowercase().contains("arm") -> "natives-linux-arm64"
    else -> "natives-linux"
}

sourceSets {
    create("serverApi") {
        java.srcDir("src/serverApi/java")
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    // These stubs exactly model the tiny server API surface used here. They
    // compile separately and are never packaged, so the provider binds to the
    // real API in hypercore-server at runtime without triggering paperweight.
    compileOnly(sourceSets.named("serverApi").map { it.output })
    testImplementation(sourceSets.named("serverApi").map { it.output })

    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-vulkan:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-shaderc:$lwjglVersion")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$nativeClassifier")
    runtimeOnly("org.lwjgl:lwjgl-shaderc:$lwjglVersion:$nativeClassifier")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named("serverApiClasses"))
}

tasks.jar {
    manifest.attributes[
        "Automatic-Module-Name"
    ] = "dev.hypercore.server.vulkan"
}

/**
 * Produces the exact directory consumed by ComputeService's optional provider
 * loader. The base server jar is intentionally not copied here.
 */
tasks.register<Sync>("assembleProviderDirectory") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("compute-providers"))
}
