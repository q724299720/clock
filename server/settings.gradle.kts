pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.9.24"
        id("org.jetbrains.kotlin.plugin.spring") version "1.9.24"
        id("org.springframework.boot") version "3.3.1"
        id("io.spring.dependency-management") version "1.1.5"
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm" ->
                    useModule("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.9.24")
                "org.jetbrains.kotlin.plugin.spring" ->
                    useModule("org.jetbrains.kotlin.plugin.spring:org.jetbrains.kotlin.plugin.spring.gradle.plugin:1.9.24")
                "org.springframework.boot" ->
                    useModule("org.springframework.boot:spring-boot-gradle-plugin:3.3.1")
                "io.spring.dependency-management" ->
                    useModule("io.spring.gradle:dependency-management-plugin:1.1.5")
            }
        }
    }
    repositories {
        maven(url = uri(rootDir.resolve("../.local-maven")))
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "smartclock-server"
