/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.dataspaceconnector.spi.jwt;

/**
 * JSON Web Token Claim Names
 * ref. <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">rfc7519 section-4.1</a>
 */
public final class JwtClaimNames {

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.1">RFC 7519 "iss" (Issuer) Claim</a>
     */
    public static final String ISSUER = "iss";


    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 "sub" (Subject) Claim</a>
     */
    public static final String SUBJECT = "sub";


    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.3">RFC 7519 "aud" (Audience) Claim</a>
     */
    public static final String AUDIENCE = "aud";


    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.4">RFC 7519 "exp" (Expiration Time) Claim</a>
     */
    public static final String EXPIRATION_TIME = "exp";


    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.5">RFC 7519 "nbf" (Not Before) Claim</a>
     */
    public static final String NOT_BEFORE = "nbf";


    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.6">RFC 7519 "iat" (Issued At) Claim</a>
     */
    public static final String ISSUED_AT = "iat";


    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.7">RFC 7519 "jti" (JWT ID) Claim</a>
     */
    public static final String JWT_ID = "jti";
}
