import io.papermc.paperweight.tasks.ExtractFromBundler

plugins {
    `java-library`
    `maven-publish`
    id("io.papermc.paperweight.core")
}

dependencies {
    mache("io.papermc:mache:26.1+build.1")
    paperclip("io.papermc:paperclip:3.0.3")
    compileOnly("org.jetbrains:annotations:24.0.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

paperweight {
    minecraftVersion = providers.gradleProperty("mcVersion")
    gitFilePatches = false
    filterPatches = false
    mainClass.set("net.minecraft.server.Main")
    bundlerJarName.set("hypercore-server")
}

tasks.matching { it.name == "rebuildResourcePatches" }.configureEach {
    dependsOn("applyResourcePatches")
}

tasks.matching { it.name == "rebuildSourcePatches" }.configureEach {
    dependsOn("applySourcePatches", "applyFeaturePatches")
}

tasks.withType<JavaCompile>().configureEach {
    val extractFromBundler = tasks.named<ExtractFromBundler>("extractFromBundler")
    dependsOn(extractFromBundler, "applySourcePatches", "applyFeaturePatches")
    classpath += files(extractFromBundler.map {
        it.serverLibraryJars.get().asFileTree.matching { include("**/*.jar") }
    })
}

tasks.withType<ProcessResources>().configureEach {
    dependsOn("applyResourcePatches")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.minecraft.server.Main",
            "Implementation-Title" to "HyperCore Server",
            "Implementation-Version" to project.version,
        )
    }
}
