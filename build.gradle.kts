
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Unit tests are JUnit 5 (see dependencies.gradle).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
