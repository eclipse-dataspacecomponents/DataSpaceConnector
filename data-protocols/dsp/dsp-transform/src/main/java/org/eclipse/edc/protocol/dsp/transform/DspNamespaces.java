/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.transform;

/**
 * Namespace used by the dataspace protocol.
 */
public interface DspNamespaces {

    String DSPACE_PREFIX = "dspace";
    String DSPACE_SCHEMA = "https://w3id.org/dspace/v0.8/"; // TODO to be defined

    String DCT_PREFIX = "dct";
    String DCT_SCHEMA = "https://purl.org/dc/terms/";
}
