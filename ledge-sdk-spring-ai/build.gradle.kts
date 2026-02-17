plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":ledge-sdk"))

    compileOnly("org.springframework.ai:spring-ai-core:1.0.0-M6")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.2.5")
    compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.springframework.ai:spring-ai-core:1.0.0-M6")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "ledge-sdk-spring-ai"
        }
    }
}
