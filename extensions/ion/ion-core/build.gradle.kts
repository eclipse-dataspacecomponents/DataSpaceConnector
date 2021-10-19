plugins {
    `java-library`
}

val jwtVersion: String by project
val okHttpVersion: String by project

repositories {
    mavenCentral()
}
dependencies {
    api(project(":extensions:iam:distributed-identity:identity-did-spi"))

    implementation("com.squareup.okhttp3:okhttp:${okHttpVersion}")

    implementation("com.google.crypto.tink:tink:1.6.1")
    implementation("io.github.erdtman:java-json-canonicalization:1.1")

    testImplementation(testFixtures(project(":common:util")))
}

publishing {
    publications {
        create<MavenPublication>("ion.ion-core") {
            artifactId = "ion.ion-core"
            from(components["java"])
        }
    }
}
