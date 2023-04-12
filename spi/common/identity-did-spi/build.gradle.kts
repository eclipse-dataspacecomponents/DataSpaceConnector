plugins {
    `java-library`
}

dependencies {
    api(project(":spi:common:core-spi"))
    // newer Nimbus versions create a version conflict with the MSAL library which uses this version as a transitive dependency
    api(root.nimbus.jwt)
}


