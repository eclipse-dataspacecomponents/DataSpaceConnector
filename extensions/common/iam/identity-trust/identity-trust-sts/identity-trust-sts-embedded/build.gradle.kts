plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":spi:common:identity-trust-spi"))
    api(project(":spi:common:jwt-spi"))

    implementation(project(":core:common:util"))
    testImplementation(testFixtures(project(":spi:common:identity-trust-spi")))
    testImplementation(project(":core:common:junit"))
    testImplementation(project(":core:common:jwt-core"))
    testImplementation(libs.nimbus.jwt)
}

