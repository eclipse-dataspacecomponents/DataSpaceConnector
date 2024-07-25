<h1 align="center">
  <br/>
      EDC Connector
  <br/>
</h1>

<div align="center">
  <a href="https://github.com/eclipse-edc/Connector/actions/workflows/verify.yaml?query=branch%3Amain">
    <img src="https://img.shields.io/github/actions/workflow/status/eclipse-edc/Connector/verify.yaml?branch=main&logo=GitHub&style=flat-square"
    alt="Tests status" />
  </a>
  <a href="https://app.codecov.io/gh/eclipse-edc/Connector">
    <img src="https://img.shields.io/codecov/c/github/eclipse-edc/Connector?style=flat-square"
    alt="Coverage" />
  </a>
  <a href="https://discord.gg/n4sD9qtjMQ">
    <img src="https://img.shields.io/badge/discord-chat-brightgreen.svg?style=flat-square&logo=discord"
    alt="Discord chat" />
  </a>
  <a href="https://search.maven.org/artifact/org.eclipse.edc/boot">
    <img src="https://img.shields.io/maven-central/v/org.eclipse.edc/boot?logo=apache-maven&style=flat-square&label=latest%20version"
    alt="Version" />
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img src="https://img.shields.io/github/license/eclipse-edc/Connector?style=flat-square&logo=apache"
    alt="License" />
  </a>
</div>
<div align="center">
  <a href="https://ci.eclipse.org/edc/job/Build-Component">
    <img src="https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fci.eclipse.org%2Fedc%2Fjob%2FPublish-Component%2F&label=snapshot-build&style=flat-square"
    alt="License" />
  </a>
  <a href="https://ci.eclipse.org/edc/job/Build-Components-Nightly">
    <img src="https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fci.eclipse.org%2Fedc%2Fjob%2FPublish-Components-Nightly%2F&label=nightly-build&style=flat-square"
    alt="License" />
  </a>
</div>


### Built with

One of the guiding principles in developing the connector is simplicity and keeping the core small and efficient with as
little external dependencies as possible to avoid version conflicts. We do not want to force any third-party
dependencies onto our users, so we aim to avoid any of the big frameworks. Of course, if you want to use them, you still
can add them to your extensions (see: [TBW]). The connector is a plain Java application built with Gradle, but it can be
embedded into any form of application deployment.

### Documentation

Developer documentation can be found under [docs/developer](docs/developer/README.md),
where the main concepts and decisions are captured as [decision records](docs/developer/decision-records/README.md).

Some more documentation can be found at [extensions](extensions/README.md), [launchers](launchers/README.md) and
[the samples repository](https://github.com/eclipse-edc/Samples).

For detailed information about the whole project, please take a look at
our [GitHub pages](https://eclipse-edc.github.io/docs).

## Getting Started

### Onboarding and first steps

If you are not yet familiar with the EDC nomenclature, we strongly advise reading the
[documentation](https://eclipse-edc.github.io/docs/#/) and/or watching the
[introductory videos](https://www.youtube.com/@eclipsedataspaceconnector9622/featured).

#### Samples

The [Samples repository](https://github.com/eclipse-edc/Samples) provides a set of examples for getting familiar with
the EDC framework and learning how to set up and use a connector. The samples begin with the very basics (e.g. learning
how to write an extension or use a configuration file) and then move on to more complex scenarios (e.g. performing
different data transfers). More samples will be added in the future, so be sure to check back regularly.

### Add Maven dependencies

Official versions are available through [MavenCentral](https://search.maven.org/search?q=org.eclipse.edc)
.
Please add the following instructions in your `build.gradle[.kts]` file (if not already present):

```kotlin
repositories {
    mavenCentral()
    // ... other maven repos
}
```

We **strongly** recommend to use official versions and only switch to snapshots if there is a clear need to do so, or
you've been instructed to do so, e.g. to verify a bugfix.

All artifacts are under the `org.eclipse.edc` group id, for example:

```kotlin
dependencies {
    implementation("org.eclipse.edc:spi:core-spi:<<version>>")
    // any other dependencies
}
```

#### Using `SNAPSHOT` versions

In addition, EDC regularly publishes snapshot versions, which are available at Sonatype's snapshot
repository. In
order to add them to your build configuration, simply add this:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    // any other repos
}
```

Then you can add snapshot dependencies by simply using the `-SNAPSHOT` version suffix:

```kotlin
dependencies {
    implementation("org.eclipse.edc:spi:core-spi:0.8.1-SNAPSHOT")
    // any other dependencies
}
```

You may check MavenCentral for a comprehensive list of all official versions.

Please be aware of the following pitfalls:

- snapshots are by definition unstable - every new snapshot replaces an old one
- this may cause unrepeatable builds
- snapshots are created irregularly, we do not have any fixed publish schedule

#### Using release versions

_We plan to have actual release versions starting some time mid 2022. Please check back soon._


> For more information about versioning please refer to the [release documentation](https://github.com/eclipse-edc/.github/blob/main/docs/developer/releases.md)

### Checkout and build from source

The project requires JDK 11+. To get started:

``` shell 
git clone git@github.com:eclipse-edc/Connector.git

cd Connector

./gradlew clean build
```

That will build the connector and run unit tests.

### [Optional] Setup your IDE

If you wish to configure your IDE/editor to automatically apply the EDC code style, please
follow [this guide](https://github.com/eclipse-edc/.github/blob/main/contributing/styleguide.md).

_Note: the style guide will be checked/enforced in GitHub Actions._

### Generate the OpenApi specification

Please refer to [this document](docs/developer/openapi.md).

## Directory structure

### `spi`

This is the primary extension point for the connector. It contains all necessary interfaces that need to be implemented
as well as essential model classes and enums. Basically, the `spi` modules defines the extent to what users can
customize and extend the code.

### `core`

Contains all absolutely essential building that is necessary to run a connector such as `TransferProcessManager`,
`ProvisionManager`, `DataFlowManager`, various model classes, the protocol engine and the policy piece. While it is
possible to build a connector with just the code from the `core` module, it will have very limited capabilities to
communicate and to interact with a data space.

### `extensions`

This contains code that extends the connector's core functionality with technology- or cloud-provider-specific code. For
example a transfer process store based on Azure CosmosDB, a secure vault based on Azure KeyVault, etc. This is where
technology- and cloud-specific implementations should go.

If someone were to create a configuration service based on Postgres, then the implementation should go into
the `extensions/database/configuration-postgres` module.

### `launchers`

Launchers are essentially connector packages that are runnable. What modules get included in the build (and thus: what
capabilities a connector has) is defined by the `build.gradle.kts` file inside the launcher subdirectory. That's also
where a Java class containing a `main` method should go. We will call that class a "runtime" and in order for the
connector to become operational the `runtime` needs to perform several important tasks (="bootstrapping"). For an
example take a look at
[this runtime](https://github.com/eclipse-edc/Samples/blob/main/other/custom-runtime/src/main/java/org/eclipse/edc/sample/runtime/CustomRuntime.java)

### `data-protocols`

Contains implementations for communication protocols a connector might use, such as DSP.

## Releases

GitHub releases are listed [here](https://github.com/eclipse-edc/Connector/releases).
Please find more information about releases in our [release approach](https://github.com/eclipse-edc/docs/blob/main/developer/releases.md).

### Roadmap

See [here](https://github.com/eclipse-edc/.github/blob/main/CONTRIBUTING.md#project-and-milestone-planning) for more information about project and
milestone planning. Scheduled and ongoing milestones are listed
[here](https://github.com/eclipse-edc/Connector/milestones).

### Tags

Available tags can be found [here](https://github.com/eclipse-edc/Connector/tags).

## Contributing

See [how to contribute](https://github.com/eclipse-edc/.github/blob/main/CONTRIBUTING.md).
