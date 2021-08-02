/*
 * Copyright (c) Microsoft Corporation.
 * All rights reserved.
 */

package com.microsoft.dagx.provision.aws.s3;

import com.microsoft.dagx.schema.s3.*;
import com.microsoft.dagx.spi.transfer.provision.ResourceDefinitionGenerator;
import com.microsoft.dagx.spi.types.domain.transfer.DataAddress;
import com.microsoft.dagx.spi.types.domain.transfer.ResourceDefinition;
import com.microsoft.dagx.spi.types.domain.transfer.TransferProcess;
import software.amazon.awssdk.regions.Region;

import static java.util.UUID.randomUUID;

/**
 * Generates S3 buckets on the client (requesting connector) that serve as data destinations.
 */
public class S3ResourceDefinitionClientGenerator implements ResourceDefinitionGenerator {

    @Override
    public ResourceDefinition generate(TransferProcess process) {
        var request = process.getDataRequest();
        if (request.getDestinationType() != null) {
            if (!S3BucketSchema.TYPE.equals(request.getDestinationType())) {
                return null;
            }
            // FIXME generate region from policy engine
            return S3BucketResourceDefinition.Builder.newInstance().id(randomUUID().toString()).bucketName(process.getId()).regionId(Region.US_EAST_1.id()).build();

        } else if (request.getDataDestination() == null || !(request.getDataDestination().getType().equals(S3BucketSchema.TYPE))) {
            return null;
        }
        DataAddress destination = request.getDataDestination();
        String id = randomUUID().toString();
        return S3BucketResourceDefinition.Builder.newInstance().id(id).bucketName(destination.getProperty(S3BucketSchema.BUCKET_NAME)).regionId(destination.getProperty(S3BucketSchema.REGION)).build();
    }
}
