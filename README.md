<h1 align="center">
  <br>
    <img alt="Logo" width="100" src="resources/media/logo.png"/>
  <br>
      Eclipse Dataspace Connector
  <br>
</h1>

<div align="center">
  <a href="https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/actions/workflows/verify.yaml">
    <img src="https://img.shields.io/github/workflow/status/eclipse-dataspaceconnector/DataSpaceConnector/Test%20Code%20(Style,%20Tests)?logo=GitHub&style=flat-square"
    alt="Tests status" />
  </a>
  <a href="https://app.codecov.io/gh/eclipse-dataspaceconnector/DataSpaceConnector">
    <img src="https://img.shields.io/codecov/c/github/eclipse-dataspaceconnector/DataSpaceConnector?style=flat-square"
    alt="Coverage" />
  </a>
  <a href="https://discord.gg/n4sD9qtjMQ">
    <img src="https://img.shields.io/badge/discord-chat-brightgreen.svg?style=flat-square&logo=discord"
    alt="Discord chat" />
  </a>
  <a href="https://search.maven.org/artifact/org.eclipse.dataspaceconnector/core-boot">
    <img src="https://img.shields.io/maven-central/v/org.eclipse.dataspaceconnector/core-boot?logo=apache-maven&style=flat-square&label=latest%20version"
    alt="Version" />
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img src="https://img.shields.io/github/license/eclipse-dataspaceconnector/DataSpaceConnector?style=flat-square&logo=apache"
    alt="License" />
  </a>
</div>

<p align="center">
  <a href="#contributing">Contribute</a> •
  <a href="https://eclipse-dataspaceconnector.github.io/docs/">Docs</a> •
  <a href="https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/issues">Issues</a> •
  <a href="https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/blob/main/LICENSE">License</a> •
  <a href="https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/discussions/1303">Q&A</a>
</p>

The Eclipse Dataspace Connector provides a framework for sovereign, inter-organizational data exchange. It will
implement the International Data Spaces standard (IDS) as well as relevant protocols associated with GAIA-X. The
connector is designed in an extensible way in order to support alternative protocols and integrate in various
ecosystems.

Please also refer to:

- The [Eclipse Project Homepage](https://projects.eclipse.org/projects/technology.dataspaceconnector)
- [International Data Spaces](https://www.internationaldataspaces.org)
- The [GAIA-X](https://gaia-x.eu) project
- The [Onboarding Guide](onboarding.md)

### Built with

One of the guiding principles in developing the connector is simplicity and keeping the core small and efficient with as
little external dependencies as possible to avoid version conflicts. We do not want to force any third-party
dependencies onto our users, so we aim to avoid any of the big frameworks. Of course, if you want to use them, you still
can add them to your extensions (see: [TBW]). The connector is a plain Java application built with Gradle, but it can be
embedded into any form of application deployment.

### Documentation

Developer documentation can be found under [docs/developer](docs/developer/),
where the main concepts and decisions are captured as [decision records](docs/developer/decision-records/).

Some more documentation can be found at [extensions](extensions/), [launchers](launchers/) and [samples](samples/).

For detailed information about the whole project, please take a look at
our [GitHub pages](https://eclipse-dataspaceconnector.github.io/docs).

## Getting Started

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
    implementation("org.eclipse.edc:spi:core-spi:0.0.1-SNAPSHOT")
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


> For more information about versioning please refer to the [release documentation](docs/developer/releases.md)

### Checkout and build from source

The project requires JDK 11+. To get started:

``` shell 
git clone git@github.com:eclipse-dataspaceconnector/DataSpaceConnector.git

cd DataSpaceConnector

./gradlew clean build
```

That will build the connector and run unit tests.

### [Optional] Setup your IDE

If you wish to configure your IDE/editor to automatically apply the EDC code style, please
follow [this guide](styleguide.md).

_Note: the style guide will be checked/enforced in GitHub Actions._

### Run your first connector

Connectors can be started using the concept of "launchers", which are essentially compositions of Java modules defined
as gradle build files.

**It is expected that everyone who wants to use the EDC will create their own launcher, customized
to the implemented use cases.**

There is an `ids-connector` launcher, which launches a simple connector that has no cloud-based extensions.
However, it needs an IDS certificate and a running DAPS. So make sure to take a look at
[this guide](./launchers/ids-connector/README.md) first.

Then run

```shell
./gradlew :launchers:ids-connector:shadowJar
java -jar launchers/ids-connector/build/libs/dataspace-connector.jar
```

Once it says `"Dataspace Connector ready"` the connector is up and running.

More information about the extension concept can be found here [TBW].

More information about shadowJar can be found [here](https://github.com/johnrengelman/shadow).

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
example take a look
at [this runtime](samples/other/custom-runtime/src/main/java/org/eclipse/edc/sample/runtime/CustomRuntime.java)

### `resources/charts`

Contains a Helm chart for the EDC runtime. You can use the `launchers/generic/Dockerfile` to build a runtime image for
your connector runtime, and deploy the resulting image to Kubernetes.

### `data-protocols`

Contains implementations for communication protocols a connector might use, such as IDS.

### `samples`

Contains code that demonstrates how the connector can be used in various scenarios. For example, it shows how to run a
connector from a unit test in order to try out functionality quickly or how to implement an outward-facing REST API for
a connector.

## Releases

GitHub releases are listed [here](https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/releases).
Please find more information about releases in our [release approach](docs/developer/releases.md).

### Roadmap

See [here](CONTRIBUTING.md#project-and-milestone-planning) for more information about project and
milestone planning. Scheduled and ongoing milestones are listed
[here](https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/milestones).

### Tags

Available tags can be found [here](https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/tags).

## Contributing

See [how to contribute](CONTRIBUTING.md).
