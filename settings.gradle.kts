// so Gradle can download JDK 21 automatically and resolve the javaCompiler toolchain during model import
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ai-commits-intellij-plugin"
