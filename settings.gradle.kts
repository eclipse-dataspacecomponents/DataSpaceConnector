/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - refactoring
 *       ZF Friedrichshafen AG - add dependency & reorder entries
 *       Fraunhofer Institute for Software and Systems Engineering - refactoring
 *       Mercedes-Benz Tech Innovation GmbH - publish public api context into dedicated swagger hub page
 *
 */

rootProject.name = "connector"

// this is needed to have access to snapshot builds of plugins
pluginManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenCentral()
    }
}

// EDC core modules --------------------------------------------------------------------------------
include(":core:common:boot")
include(":core:common:connector-core")
include(":core:common:junit")
include(":core:common:token-core")
include(":core:common:edr-store-core")

include(":core:common:lib:boot-lib")
include(":core:common:lib:policy-evaluator-lib")
include(":core:common:lib:policy-engine-lib")
include(":core:common:lib:http-lib")
include(":core:common:lib:keys-lib")
include(":core:common:lib:query-lib")
include(":core:common:lib:store-lib")
include(":core:common:lib:state-machine-lib")
include(":core:common:lib:crypto-common-lib")
include(":core:common:lib:json-lib")
include(":core:common:lib:json-ld-lib")
include(":core:common:lib:transform-lib")
include(":core:common:lib:validator-lib")
include(":core:common:lib:util-lib")

include(":core:control-plane:control-plane-catalog")
include(":core:control-plane:control-plane-contract")
include(":core:control-plane:control-plane-core")
include(":core:control-plane:control-plane-aggregate-services")
include(":core:control-plane:control-plane-transform")
include(":core:control-plane:control-plane-transfer")
include(":core:control-plane:lib:control-plane-policies-lib")

include(":core:data-plane:data-plane-util")
include(":core:data-plane:data-plane-core")

include(":core:data-plane-selector:data-plane-selector-core")

include(":core:policy-monitor:policy-monitor-core")


// modules that provide implementations for data ingress/egress ------------------------------------
include(":data-protocols:dsp:dsp-http-api-configuration")
include(":data-protocols:dsp:dsp-catalog")
include(":data-protocols:dsp:dsp-catalog:dsp-catalog-http-api")
include(":data-protocols:dsp:dsp-catalog:dsp-catalog-http-dispatcher")
include(":data-protocols:dsp:dsp-catalog:dsp-catalog-transform")
include(":data-protocols:dsp:dsp-spi")
include(":data-protocols:dsp:dsp-negotiation")
include(":data-protocols:dsp:dsp-negotiation:dsp-negotiation-http-api")
include(":data-protocols:dsp:dsp-negotiation:dsp-negotiation-http-dispatcher")
include(":data-protocols:dsp:dsp-negotiation:dsp-negotiation-transform")
include(":data-protocols:dsp:dsp-http-core")
include(":data-protocols:dsp:dsp-http-spi")
include(":data-protocols:dsp:dsp-transfer-process")
include(":data-protocols:dsp:dsp-transfer-process:dsp-transfer-process-http-api")
include(":data-protocols:dsp:dsp-transfer-process:dsp-transfer-process-http-dispatcher")
include(":data-protocols:dsp:dsp-transfer-process:dsp-transfer-process-transform")
include(":data-protocols:dsp:dsp-version:dsp-version-http-api")

// modules for technology- or cloud-provider extensions --------------------------------------------
include(":extensions:common:api:api-core")
include(":extensions:common:api:api-observability")
include(":extensions:common:api:lib:management-api-lib")
include(":extensions:common:api:version-api")
include(":extensions:common:auth:auth-basic")
include(":extensions:common:auth:auth-tokenbased")
include(":extensions:common:auth:auth-delegated")
include(":extensions:common:auth:auth-configuration")
include(":extensions:common:crypto:ldp-verifiable-credentials")
include(":extensions:common:crypto:jwt-verifiable-credentials")
include(":extensions:common:crypto:lib:jws2020-lib")

include(":extensions:common:http:lib:jersey-providers-lib")

include(":extensions:common:configuration:configuration-filesystem")
include(":extensions:common:events:events-cloud-http")
include(":extensions:common:http")
include(":extensions:common:http:jersey-core")
include(":extensions:common:http:jersey-micrometer")
include(":extensions:common:http:jetty-core")
include(":extensions:common:http:jetty-micrometer")
include(":extensions:common:iam:decentralized-identity")
include(":extensions:common:iam:decentralized-identity:identity-did-core")
include(":extensions:common:iam:decentralized-identity:identity-did-web")
include(":extensions:common:iam:iam-mock")
include(":extensions:common:iam:oauth2:oauth2-daps")
include(":extensions:common:iam:oauth2:oauth2-core")
include(":extensions:common:iam:oauth2:oauth2-client")
include(":extensions:common:iam:oauth2:oauth2-service")
include(":extensions:common:iam:verifiable-credentials")
include(":extensions:common:iam:identity-trust")
include(":extensions:common:iam:identity-trust:identity-trust-transform")
include(":extensions:common:iam:identity-trust:identity-trust-service")
include(":extensions:common:iam:identity-trust:identity-trust-core")
include(":extensions:common:iam:identity-trust:identity-trust-sts")
include(":extensions:common:iam:identity-trust:identity-trust-sts:identity-trust-sts-embedded")
include(":extensions:common:iam:identity-trust:identity-trust-sts:identity-trust-sts-core")
include(":extensions:common:iam:identity-trust:identity-trust-sts:identity-trust-sts-remote-client")
include(":extensions:common:iam:identity-trust:identity-trust-sts:identity-trust-sts-api")
include(":extensions:common:iam:identity-trust:identity-trust-sts:identity-trust-sts-client-configuration")
include(":extensions:common:iam:identity-trust:identity-trust-sts:lib:identity-trust-sts-remote-lib")
include(":extensions:common:iam:identity-trust:identity-trust-issuers-configuration")

include(":extensions:common:json-ld")
include(":extensions:common:metrics:micrometer-core")
include(":extensions:common:monitor:monitor-jdk-logger")
include(":extensions:common:sql:sql-core")
include(":extensions:common:sql:sql-lease")
include(":extensions:common:sql:sql-pool:sql-pool-apache-commons")
include(":extensions:common:transaction")
include(":extensions:common:transaction:transaction-atomikos")
include(":extensions:common:transaction:transaction-local")
include(":extensions:common:validator:validator-data-address-http-data")
include(":extensions:common:validator:validator-data-address-kafka")
include(":extensions:common:vault:vault-hashicorp")
include(":extensions:common:store:sql:edr-index-sql")

include(":extensions:common:api:control-api-configuration")
include(":extensions:common:api:management-api-configuration")
include(":extensions:common:api:management-api-json-ld-context")


include(":extensions:control-plane:api:control-plane-api")
include(":extensions:control-plane:api:control-plane-api-client")
include(":extensions:control-plane:api:management-api")
include(":extensions:control-plane:api:management-api:asset-api")
include(":extensions:control-plane:api:management-api:secrets-api")
include(":extensions:control-plane:api:management-api:catalog-api")
include(":extensions:control-plane:api:management-api:contract-agreement-api")
include(":extensions:control-plane:api:management-api:contract-definition-api")
include(":extensions:control-plane:api:management-api:contract-negotiation-api")
include(":extensions:control-plane:api:management-api:management-api-test-fixtures")
include(":extensions:control-plane:api:management-api:policy-definition-api")
include(":extensions:control-plane:api:management-api:transfer-process-api")
include(":extensions:control-plane:api:management-api:edr-cache-api")
include(":extensions:control-plane:transfer:transfer-data-plane")
include(":extensions:control-plane:transfer:transfer-data-plane-signaling")
include(":extensions:control-plane:transfer:transfer-pull-http-receiver")
include(":extensions:control-plane:transfer:transfer-pull-http-dynamic-receiver")
include(":extensions:control-plane:provision:provision-http")

include(":extensions:control-plane:store:sql:asset-index-sql")
include(":extensions:control-plane:store:sql:contract-definition-store-sql")
include(":extensions:control-plane:store:sql:contract-negotiation-store-sql")
include(":extensions:control-plane:store:sql:control-plane-sql")
include(":extensions:control-plane:store:sql:policy-definition-store-sql")
include(":extensions:control-plane:store:sql:transfer-process-store-sql")
include(":extensions:control-plane:callback:callback-event-dispatcher")
include(":extensions:control-plane:callback:callback-http-dispatcher")
include(":extensions:control-plane:callback:callback-static-endpoint")
include(":extensions:control-plane:edr:edr-store-receiver")


include(":extensions:data-plane:data-plane-client")
include(":extensions:data-plane:data-plane-client-embedded")
include(":extensions:data-plane:data-plane-control-api")
include(":extensions:data-plane:data-plane-self-registration")
include(":extensions:data-plane:data-plane-signaling:data-plane-signaling-api")
include(":extensions:data-plane:data-plane-signaling:data-plane-signaling-api-configuration")
include(":extensions:data-plane:data-plane-signaling:data-plane-signaling-client")
include(":extensions:data-plane:data-plane-signaling:data-plane-signaling-transform")
include(":extensions:data-plane:data-plane-public-api")
include(":extensions:data-plane:data-plane-public-api-v2")

include(":extensions:data-plane:data-plane-http")
include(":extensions:data-plane:data-plane-http-oauth2")
include(":extensions:data-plane:data-plane-http-oauth2-core")
include(":extensions:data-plane:data-plane-integration-tests")
include(":extensions:data-plane:store:sql:data-plane-store-sql")
include(":extensions:data-plane:store:sql:accesstokendata-store-sql")
include(":extensions:data-plane:data-plane-kafka")

include(":extensions:data-plane-selector:data-plane-selector-api")
include(":extensions:data-plane-selector:data-plane-selector-client")
include(":extensions:data-plane-selector:data-plane-selector-control-api")
include(":extensions:data-plane-selector:store:sql:data-plane-instance-store-sql")

include(":extensions:policy-monitor:store:sql:policy-monitor-store-sql")


// modules for launchers, i.e. runnable compositions of the app ------------------------------------
include(":launchers:dpf-selector")
include(":launchers:sts-server")

// extension points for a connector ----------------------------------------------------------------
include(":spi:common:auth-spi")
include(":spi:common:boot-spi")
include(":spi:common:core-spi")
include(":spi:common:data-address:data-address-http-data-spi")
include(":spi:common:data-address:data-address-kafka-spi")
include(":spi:common:http-spi")
include(":spi:common:keys-spi")
include(":spi:common:identity-did-spi")
include(":spi:common:json-ld-spi")
include(":spi:common:jwt-spi")
include(":spi:common:token-spi")
include(":spi:common:oauth2-spi")
include(":spi:common:policy-engine-spi")
include(":spi:common:policy-model")
include(":spi:common:transaction-datasource-spi")
include(":spi:common:transaction-spi")
include(":spi:common:transform-spi")
include(":spi:common:validator-spi")
include(":spi:common:web-spi")
include(":spi:common:verifiable-credentials-spi")
include(":spi:common:identity-trust-spi")
include(":spi:common:identity-trust-sts-spi")
include(":spi:common:edr-store-spi")


include(":spi:control-plane:asset-spi")
include(":spi:control-plane:catalog-spi")
include(":spi:control-plane:contract-spi")
include(":spi:control-plane:control-plane-spi")
include(":spi:control-plane:transfer-data-plane-spi")
include(":spi:control-plane:policy-spi")
include(":spi:control-plane:transfer-spi")
include(":spi:control-plane:secrets-spi")
include(":spi:control-plane:control-plane-api-client-spi")

include(":spi:data-plane:data-plane-spi")
include(":spi:data-plane:data-plane-http-spi")

include(":spi:data-plane-selector:data-plane-selector-spi")
include(":spi:policy-monitor:policy-monitor-spi")

// modules for tests ------------------------------------------------------------------------
include(":tests:junit-base")

// modules for system tests ------------------------------------------------------------------------
include(":system-tests:e2e-transfer-test:control-plane")
include(":system-tests:e2e-transfer-test:data-plane")
include(":system-tests:e2e-transfer-test:runner")
include(":system-tests:e2e-dataplane-tests:runtimes:data-plane")
include(":system-tests:e2e-dataplane-tests:tests")
include(":system-tests:management-api:management-api-test-runner")
include(":system-tests:management-api:management-api-test-runtime")
include(":system-tests:version-api:version-api-test-runtime")
include(":system-tests:version-api:version-api-test-runner")
include(":system-tests:protocol-test")
include(":system-tests:sts-api:sts-api-test-runner")
include(":system-tests:sts-api:sts-api-test-runtime")
include(":system-tests:telemetry:telemetry-test-runner")
include(":system-tests:telemetry:telemetry-test-runtime")

include(":version-catalog")
