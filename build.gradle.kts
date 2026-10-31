plugins {
    java
}

group = "dev.tokensmp"
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API only: no other runtime dependencies. SQLite JDBC ships with Paper itself.
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of((property("javaVersion") as String).toInt()))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    // Only plugin.yml is templated, so config.yml / messages.yml are copied untouched.
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("TokenSMP-${project.version}.jar")
}
