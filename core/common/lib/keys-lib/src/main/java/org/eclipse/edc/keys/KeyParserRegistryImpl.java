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

package org.eclipse.edc.keys;

import org.eclipse.edc.keys.spi.KeyParser;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.spi.result.Result;

import java.security.Key;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class KeyParserRegistryImpl implements KeyParserRegistry {
    private final List<KeyParser> parsers = new ArrayList<>();

    @Override
    public void register(KeyParser parser) {
        parsers.add(parser);
    }

    @Override
    public Result<Key> parse(String encoded) {
        return parsers.stream().filter(kp -> kp.canHandle(encoded))
                .findFirst()
                .map(kp -> kp.parse(encoded))
                .orElseGet(() -> Result.failure("No parser found that can handle that format."));
    }

    @Override
    public Result<PublicKey> parsePublic(String encoded) {
        return parsers.stream().filter(kp -> kp.canHandle(encoded))
                .findFirst()
                .map(kp -> kp.parsePublic(encoded).compose(k -> Result.success((PublicKey) k)))
                .orElseGet(() -> Result.failure("No parser found that can handle that format."));
    }
}
