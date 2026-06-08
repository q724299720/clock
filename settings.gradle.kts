pluginManagement {
    plugins {
        id("com.android.application") version "8.5.0"
        id("org.jetbrains.kotlin.android") version "1.9.24"
        id("org.jetbrains.kotlin.jvm") version "1.9.24"
        id("org.jetbrains.kotlin.plugin.spring") version "1.9.24"
        id("com.google.dagger.hilt.android") version "2.51.1"
        id("com.google.devtools.ksp") version "1.9.24-1.0.20"
        id("org.springframework.boot") version "3.3.1"
        id("io.spring.dependency-management") version "1.1.5"
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application" ->
                    useModule("com.android.tools.build:gradle:8.5.0")
                "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:1.9.24")
                "org.jetbrains.kotlin.jvm" ->
                    useModule("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.9.24")
                "org.jetbrains.kotlin.plugin.spring" ->
                    useModule("org.jetbrains.kotlin.plugin.spring:org.jetbrains.kotlin.plugin.spring.gradle.plugin:1.9.24")
                "com.google.dagger.hilt.android" ->
                    useModule("com.google.dagger.hilt.android:com.google.dagger.hilt.android.gradle.plugin:2.51.1")
                "com.google.devtools.ksp" ->
                    useModule("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.9.24-1.0.20")
                "org.springframework.boot" ->
                    useModule("org.springframework.boot:spring-boot-gradle-plugin:3.3.1")
                "io.spring.dependency-management" ->
                    useModule("io.spring.gradle:dependency-management-plugin:1.1.5")
            }
        }
    }
    repositories {
        maven(url = uri(rootDir.resolve(".local-maven")))
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 农历库 cn.6tail:lunar 在 mavenCentral 提供
    }
}

rootProject.name = "SmartClock"
include(":app")
include(":server")
