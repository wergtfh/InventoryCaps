plugins {
    java
}

group = "com.reconnaissance"
version = "1.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

 tasks.jar {
    archiveBaseName.set("ReconnaissanceFactions")
    archiveVersion.set(project.version.toString())
}
