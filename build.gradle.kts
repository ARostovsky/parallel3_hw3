import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.31"

}

version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://dl.bintray.com/devexperts/Maven/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("com.devexperts.lincheck:lincheck:2.0")
    testImplementation("junit:junit:4.12")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}