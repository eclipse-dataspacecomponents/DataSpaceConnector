/*
 * Copyright (c) Microsoft Corporation.
 * All rights reserved.
 */

package com.microsoft.dagx.provision.aws.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
class S3BucketProvisionedResourceTest {

    private S3BucketProvisionedResource provisionedResource;

    @Test
    void verifyDeserialize() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, provisionedResource);

        S3BucketProvisionedResource deserialized = mapper.readValue(writer.toString(), S3BucketProvisionedResource.class);

        assertNotNull(deserialized);
        assertEquals("region", deserialized.getRegion());
        assertEquals("bucket", deserialized.getBucketName());
    }

    @BeforeEach
    void setUp() {
        provisionedResource = S3BucketProvisionedResource.Builder.newInstance()
                .id(randomUUID().toString()).transferProcessId("123").resourceDefinitionId(randomUUID().toString()).region("region").bucketName("bucket").build();
    }
}
