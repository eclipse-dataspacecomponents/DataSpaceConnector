plugins {
    `java-library`
}

val jwtVersion: String by project
val rsApi: String by project


dependencies {
    implementation(project(":extensions:azure:events-config"))
    implementation(project(":extensions:iam:distributed-identity:identity-did-spi"))

    // third party
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:${rsApi}")
    implementation("org.quartz-scheduler:quartz:2.3.0")
}

publishing {
    publications {
        create<MavenPublication>("iam.registration-service") {
            artifactId = "iam.registration-service"
            from(components["java"])
        }
    }
}
