plugins {
    java
    application
    kotlin("jvm") version "2.0.21"
}

group = "com.keychenga"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}


tasks {
    distZip {
        enabled = false
    }
    distTar {
        enabled = false
    }
}

application {
    mainClass.set("com.keychenga.KeychengaKt")
    applicationDefaultJvmArgs = listOf(
        "-Xmx1g",
        "-Xms128m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=.",
        "-XX:+ExitOnOutOfMemoryError",
        "-Dapple.awt.application.appearance=system",
        "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass,
        )
    }
    val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Exec>("packageMacApp") {
    dependsOn("jar")
    doFirst {
        delete("build/Keychenga.app")
    }

    commandLine(
        "jpackage",
        "--input", "build/libs",
        "--main-jar", "keychenga-$version.jar",
        "--main-class", application.mainClass.get(),
        "--name", "Keychenga",
        "--dest", "build/",
        "--type", "app-image",
//        "--icon", "src/main/resources/keychenga.icns",
        "--app-version", "1.0.0",
        "--vendor", "Your Name",
        "--mac-package-name", "Keychenga",
        "--mac-package-identifier", "com.keychenga",
        "--java-options", "-Xmx1g",
        "--java-options", "-Xms128m",
        "--java-options", "-XX:+HeapDumpOnOutOfMemoryError",
        "--java-options", "-XX:HeapDumpPath=.",
        "--java-options", "-XX:+ExitOnOutOfMemoryError",
        "--java-options", "-Dapple.awt.application.appearance=system",
        "--java-options", "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
        "--java-options", "--add-exports=java.desktop/sun.awt=ALL-UNNAMED"
    )
}

tasks.assemble {
    finalizedBy("packageMacApp")
}