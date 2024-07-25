/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.keys.keyparsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyConverter;
import com.nimbusds.jose.jwk.OctetKeyPair;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.eclipse.edc.keys.spi.KeyParser;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.List;

/**
 * KeyParser that can parse a private key represented in JWK format. Specifically, it can handle the following keys:
 * <ul>
 *     <li>RSA Keys</li>
 *     <li>EC Keys, tested with P256, P384, P521, secp256k1</li>
 *     <li>EdDSA Keys, only Ed25519 and X25519 (no Ed448)</li>
 * </ul>
 */
public class JwkParser implements KeyParser {

    public static final String ERROR_NO_KEY = "The provided key material was in JSON format, but did not contain any (readable) JWK key";
    protected final Monitor monitor;
    private final ObjectMapper objectMapper;

    public JwkParser(ObjectMapper objectMapper, Monitor monitor) {
        this.objectMapper = objectMapper;
        this.monitor = monitor;
    }

    /**
     * Checks if the given raw string represents a JSON structure.
     *
     * @param encoded The raw string to be checked.
     * @return true if the raw string is a valid JSON object, false otherwise.
     */
    @Override
    public boolean canHandle(String encoded) {
        try (var parser = objectMapper.createParser(encoded)) {
            parser.nextToken();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Attempts to parse a given raw String as JWT and convert it to a {@link PrivateKey}. This is straight forward for
     * EC and RSA keys, but for EdDSA keys (represented by Nimbus' {@link OctetKeyPair}) no direct conversion exists, so
     * they must be converted to their ASN.1 binary representation first, and then read back into a Java {@link PrivateKey}
     *
     * @param encoded The raw JSON structure
     * @return the converted {@link PrivateKey}, or a failure indicating what went wrong.
     */
    @Override
    public Result<Key> parse(String encoded) {
        try {


            var jwk = JWK.parse(encoded);
            // OctetKeyPairs (OKP) need special handling, as they can't be easily converted to a Java PrivateKey
            if (jwk instanceof OctetKeyPair okp) {
                return parseOctetKeyPair(okp).map(key -> key);
            }

            var list = KeyConverter.toJavaKeys(List.of(jwk)); // contains an entry each for public and private key

            return list.stream()
                    .filter(k -> k instanceof PrivateKey)
                    .findFirst()
                    .or(() -> list.stream().filter(k -> k instanceof PublicKey).findFirst())
                    .map(Result::success)
                    .orElse(Result.failure(ERROR_NO_KEY));
        } catch (ParseException | NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            monitor.warning("Parser error", e);
            return Result.failure("Parser error: " + e.getMessage());
        }
    }

    @Override
    public Result<Key> parsePublic(String encoded) {
        try {
            var jwk = JWK.parse(encoded).toPublicJWK();
            // OctetKeyPairs (OKP) need special handling, as they can't be easily converted to a Java PrivateKey
            if (jwk instanceof OctetKeyPair okp) {
                return parseOctetKeyPair(okp.toPublicJWK()).map(key -> key);
            }
            var list = KeyConverter.toJavaKeys(List.of(jwk)); // contains an entry each for public and private key

            return list.stream()
                    .findFirst()
                    .map(Result::success)
                    .orElse(Result.failure(ERROR_NO_KEY));
        } catch (ParseException | NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            monitor.warning("Parser error", e);
            return Result.failure("Parser error: " + e.getMessage());
        }
    }

    private Result<? extends Key> parseOctetKeyPair(OctetKeyPair okp) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        var d = okp.getDecodedD();
        var x = okp.getDecodedX();

        if (d == null && x == null) {
            return Result.failure(ERROR_NO_KEY);
        }

        // if the JWK contains a private key, return that, otherwise return just the public key
        if (d != null) {
            return readPrivateKey(okp.getCurve(), d);
        } else {
            return readPublicKey(okp.getCurve(), x);
        }

    }

    private Result<PrivateKey> readPrivateKey(Curve curve, byte[] decodedD) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        // inspired by https://stackoverflow.com/a/54073962
        PrivateKeyInfo privKeyInfo;
        KeyFactory keyFactory;
        if (curve == Curve.Ed25519) {
            keyFactory = KeyFactory.getInstance(Curve.Ed25519.getName());
            privKeyInfo = new PrivateKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519), new DEROctetString(decodedD));

        } else if (curve == Curve.X25519) {
            keyFactory = KeyFactory.getInstance(Curve.X25519.getName());
            privKeyInfo = new PrivateKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_X25519), new DEROctetString(decodedD));
        } else {
            return Result.failure("Cannot parse an OctetKeyPair with Curve %s".formatted(curve));
        }

        var pkcs8KeySpec = new PKCS8EncodedKeySpec(privKeyInfo.getEncoded());
        var jcaPrivateKey = keyFactory.generatePrivate(pkcs8KeySpec);
        return Result.success(jcaPrivateKey);
    }

    private Result<PublicKey> readPublicKey(Curve curve, byte[] decodedX) throws InvalidKeySpecException, IOException, NoSuchAlgorithmException {
        KeyFactory keyFactory;
        X509EncodedKeySpec x509KeySpec;

        // Wrap public key in ASN.1 format, so we can use X509EncodedKeySpec to read it
        if (curve == Curve.Ed25519) {
            keyFactory = KeyFactory.getInstance(Curve.Ed25519.getName());
            var publicKeyInfo = new SubjectPublicKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519), decodedX);
            x509KeySpec = new X509EncodedKeySpec(publicKeyInfo.getEncoded());
        } else if (curve == Curve.X25519) {
            keyFactory = KeyFactory.getInstance(Curve.X25519.getName());
            var publicKeyInfo = new SubjectPublicKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_X25519), decodedX);
            x509KeySpec = new X509EncodedKeySpec(publicKeyInfo.getEncoded());
        } else {
            return Result.failure("Cannot parse an OctetKeyPair with Curve %s".formatted(curve));

        }

        return Result.success(keyFactory.generatePublic(x509KeySpec));
    }
}
