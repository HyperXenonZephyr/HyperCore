pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
    }

    plugins {
        id("io.papermc.paperweight.core") version providers.gradleProperty("paperweightVersion").get()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "hypercore-paperweight"

include("hypercore-server")
include("hypercore-vulkan-provider")
