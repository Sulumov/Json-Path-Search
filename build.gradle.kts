plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.jsonpathsearch"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2023.3")
        
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.jsonpathsearch"
        name = "JSON Path Search"
        version = project.version.toString()
        description = """
            Search JSON files for nested property paths and navigate to their locations.
            <br><br>
            <b>Usage:</b>
            <ul>
                <li>Select a property path like 'balance.main.title'</li>
                <li>Press Cmd+Option+F (macOS) or Ctrl+Alt+Shift+F (Windows/Linux)</li>
                <li>Click on a result to navigate to the JSON property</li>
            </ul>
        """.trimIndent()
        
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
        
        vendor {
            name = "JSON Path Search"
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}
