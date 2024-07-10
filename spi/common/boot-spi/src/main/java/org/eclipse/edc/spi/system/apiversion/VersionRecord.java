/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.spi.system.apiversion;

import java.time.Instant;

/**
 * Contains version information about a single API
 *
 * @param version     The full version as SemVer string
 * @param urlPath     The path element, e.g. /v3
 * @param lastUpdated When the API's version was last bumped
 * @param maturity    Maturity level of an API, can be "stable", "deprecated", "alpha" or "beta"
 */
public record VersionRecord(String version, String urlPath, Instant lastUpdated, String maturity) {
}
