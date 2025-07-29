plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    java
}

group = "dev.arubik"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.mockito:mockito-core:5.1.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    
    compileTestJava {
        options.encoding = "UTF-8"
    }
    
    shadowJar {
        archiveClassifier.set("")
        relocate("com.google.gson", "dev.arubik.blobcraft.libs.gson")
        
        manifest {
            attributes(
                "Main-Class" to "dev.arubik.blobcraft.Main"
            )
        }
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    test {
        useJUnitPlatform()
    }
    
    processResources {
        val props = mapOf(
            "version" to version,
            "name" to project.name,
            "main" to "dev.arubik.blobcraft.Main"
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
