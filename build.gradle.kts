plugins {
    java
    application
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

application {
    mainClass.set("core.DTNSim") // Sesuaikan dengan lokasi kelas utama
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("lib/DTNConsoleConnection.jar"))
    implementation(files("lib/ECLA.jar"))
    implementation(files("lib/fastjson-1.2.7.jar"))
    implementation(files("lib/jFuzzyLogic.jar"))
    implementation(files("lib/lombok.jar"))
    implementation(files("lib/uncommons-maths-1.2.1.jar"))
}

sourceSets.main {
    java.srcDirs("src")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "core.DTNSim"
    }
}
