/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - Initial implementation
 *
 */

package org.eclipse.edc.keys.spi;

import org.eclipse.edc.spi.result.Result;

import java.security.Key;

/**
 * Handles the parsing of serialized security keys of a give type. Depending on the actual format (JWK, PEM) and the key type
 * (RSA, Elliptic Curve, EdDSA,...) the serialized form contains just the private key, the private key plus the public key, or just the
 * public key.
 * <p>
 * Implementors must adhere to the following principle:
 * <ul>
 *     <li>If the serialized form contains the private key, return a {@link java.security.PrivateKey}</li>
 *     <li>If the serialized form does not contain a private key, return a {@link java.security.PublicKey}</li>
 *     <li>In all other cases return an error.</li>
 * </ul>
 */
public interface KeyParser {

    /**
     * Returns true if this parser can deserialize the string representation of the given key.
     */
    boolean canHandle(String encoded);

    /**
     * Parses the encoded key. If the encoded string is invalid, or the parser can't handle the input,
     * it must return a {@link Result#failure(String)}, it must never throw an exception.
     * <p>
     * If the given key material contains private key data, return a {@link java.security.PrivateKey}, even if a public key is also present.
     * If the given key material does not contain private key data, just public key data, return a {@link java.security.PublicKey}. In all
     * other cases, a {@link Result#failure(String)} is returned.
     *
     * @param encoded serialized/encoded key material.
     * @return Either a {@link java.security.PrivateKey}, a {@link java.security.PublicKey} or a failure.
     */
    Result<Key> parse(String encoded);

    /**
     * Parses the encoded key as public key. If the encoded string is invalid, or the parser can't handle the input,
     * it must return a {@link Result#failure(String)}, it must never throw an exception.
     * <p>
     * If the given key material contains public and private key data, the parser attempts to remove the private key data,
     * returning only the public part of the key as {@link java.security.PublicKey}.
     * If the given key material does not contain private key data, just public key data, returns a {@link java.security.PublicKey}. In all
     * other cases, a {@link Result#failure(String)} is returned, for example, when a private key cannot be converted into a public key.
     *
     * @param encoded serialized/encoded key material.
     * @return Either a {@link java.security.PublicKey} or a failure.
     */
    default Result<Key> parsePublic(String encoded) {
        return parse(encoded);
    }
}
