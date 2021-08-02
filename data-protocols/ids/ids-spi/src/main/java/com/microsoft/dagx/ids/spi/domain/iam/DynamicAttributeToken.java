/*
 * Copyright (c) Microsoft Corporation.
 * All rights reserved.
 */

package com.microsoft.dagx.ids.spi.domain.iam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.microsoft.dagx.ids.spi.domain.DefaultValues.CONTEXT;

/**
 * Token format as specified by IDS.
 *
 * .cf https://industrialdataspace.jiveon.com/docs/DOC-2524
 */
@JsonDeserialize(builder = DynamicAttributeToken.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DynamicAttributeToken {
    private static final String ID_BASE = "https://w3id.org/idsa/autogen/dynamicAttributeToken/";

    @JsonProperty("@context")
    private String context = CONTEXT;

    @SuppressWarnings("FieldCanBeLocal")
    @JsonProperty("@type")
    private String type = "ids:DynamicAttributeToken";

    @JsonProperty("@id")
    private String id;

    @JsonProperty("ids:tokenValue")
    private String tokenValue;

    @JsonProperty("ids:tokenFormat")
    private TokenFormat tokenFormat = TokenFormat.JWT;

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getContext() {
        return context;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public TokenFormat getTokenFormat() {
        return tokenFormat;
    }

    private DynamicAttributeToken() {
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private DynamicAttributeToken token;

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty("@context")
        public Builder context(String context) {
            token.context = context;
            return this;
        }

        @JsonProperty("@id")
        public Builder id(String id) {
            token.id = id;
            return this;
        }

        public Builder relativeId(String id) {
            token.id = ID_BASE+id;
            return this;
        }

        @JsonProperty("@type")
        public Builder type(String type) {
            token.type = type;
            return this;
        }

        @JsonProperty("ids:tokenValue")
        public Builder tokenValue(String tokenValue) {
            token.tokenValue = tokenValue;
            return this;
        }

        @JsonProperty("ids:tokenFormat")
        public Builder tokenFormat(TokenFormat tokenFormat) {
            token.tokenFormat = tokenFormat;
            return this;
        }

        public DynamicAttributeToken build() {
            Objects.requireNonNull(token.id, "Property 'id' must be specified");
            Objects.requireNonNull(token.tokenValue, "Property 'tokenValue' must be specified");
            Objects.requireNonNull(token.tokenFormat, "Property 'tokenFormat' must be specified");
            return token;
        }

        private Builder() {
            token = new DynamicAttributeToken();
        }
    }

}
