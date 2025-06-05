import java.io.BufferedReader

plugins {
    id("com.gradleup.shadow") version "8.3.6" // Import shadow API.
    java // Tell gradle this is a java project.
    eclipse // Import eclipse plugin for IDE integration.
    kotlin("jvm") version "2.1.21" // Import kotlin jvm plugin for kotlin/java integration.
}

val commitHash = Runtime
    .getRuntime()
    .exec(arrayOf("git", "rev-parse", "--short=10", "HEAD"))
    .let { process ->
        process.waitFor()
        val output = process.inputStream.use {
            it.bufferedReader().use(BufferedReader::readText)
        }
        process.destroy()
        output.trim()
    }

group = "nl.skbotnl.shopbridge-og" // Declare bundle identifier.
version = "1.1.6" // Declare plugin version (will be in .jar).
val apiVersion = "1.19" // Declare minecraft server target version.

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to version,
        "apiVersion" to apiVersion
    )

    inputs.properties(props) // Indicates to rerun if version changes.

    filesMatching("plugin.yml") {
        expand(props)
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("https://repo.purpurmc.org/snapshots")
    }
    maven {
        url = uri("https://repo.purpurmc.org/snapshots")
    }
    maven {
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("com.ghostchu:quickshop-api:5.2.0.7")
    compileOnly(files("libs/AreaShop-2.9.1.jar"))
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.8")
    implementation("com.ghostchu.quickshop.compatibility:common:5.2.0.7")
}

val targetJavaVersion = 17
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("part")
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "apiVersion" to apiVersion
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }

    from("LICENSE") {
        into("/")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetJavaVersion)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}
