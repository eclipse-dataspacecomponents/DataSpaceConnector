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

package org.eclipse.edc.jsonld;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.apicatalog.jsonld.loader.FileLoader;
import com.apicatalog.jsonld.loader.HttpLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;

/**
 * Implementation of the {@link JsonLd} interface that uses the Titanium library for all JSON-LD operations.
 */
public class TitaniumJsonLd implements JsonLd {
    private final Monitor monitor;
    private final Map<String, String> additionalNamespaces = new HashMap<>();
    private final CachedDocumentLoader documentLoader;

    public TitaniumJsonLd(Monitor monitor) {
        this(monitor, JsonLdConfiguration.Builder.newInstance().build());
    }

    public TitaniumJsonLd(Monitor monitor, JsonLdConfiguration configuration) {
        this.monitor = monitor;
        this.documentLoader = new CachedDocumentLoader(configuration);
    }

    @Override
    public Result<JsonObject> expand(JsonObject json) {
        try {
            var document = JsonDocument.of(json);
            var expanded = com.apicatalog.jsonld.JsonLd.expand(document)
                    .options(new JsonLdOptions(documentLoader))
                    .get();
            if (expanded.size() > 0) {
                return Result.success(expanded.getJsonObject(0));
            }
            return Result.success(Json.createObjectBuilder().build());
        } catch (JsonLdError error) {
            monitor.warning("Error expanding JSON-LD structure", error);
            return Result.failure(error.getMessage());
        }
    }

    @Override
    public Result<JsonObject> compact(JsonObject json) {
        try {
            var document = JsonDocument.of(json);
            var jsonFactory = Json.createBuilderFactory(Map.of());
            var contextDocument = JsonDocument.of(jsonFactory.createObjectBuilder()
                    .add(CONTEXT, createContextObject())
                    .build());
            var compacted = com.apicatalog.jsonld.JsonLd.compact(document, contextDocument).get();
            return Result.success(compacted);
        } catch (JsonLdError e) {
            monitor.warning("Error compacting JSON-LD structure", e);
            return Result.failure(e.getMessage());
        }
    }

    @Override
    public void registerNamespace(String prefix, String contextIri) {
        additionalNamespaces.put(prefix, contextIri);
    }

    @Override
    public void registerCachedDocument(String contextUrl, File file) {
        documentLoader.register(contextUrl, file);
    }

    private JsonObject createContextObject() {
        var builder = Json.createObjectBuilder();
        additionalNamespaces.forEach(builder::add);
        return builder.build();
    }

    private static class CachedDocumentLoader implements DocumentLoader {

        private final Map<String, File> cache = new HashMap<>();
        private final DocumentLoader loader;

        CachedDocumentLoader(JsonLdConfiguration configuration) {
            loader = new SchemeRouter()
                    .set("http", configuration.isHttpEnabled() ? HttpLoader.defaultInstance() : null)
                    .set("https", configuration.isHttpsEnabled() ? HttpLoader.defaultInstance() : null)
                    .set("file", new FileLoader());
        }

        @Override
        public Document loadDocument(URI url, DocumentLoaderOptions options) throws JsonLdError {
            var uri = Optional.of(url.toString())
                    .map(cache::get)
                    .map(File::toURI)
                    .orElse(url);

            return loader.loadDocument(uri, options);
        }

        public void register(String contextUrl, File file) {
            cache.put(contextUrl, file);
        }
    }
}
