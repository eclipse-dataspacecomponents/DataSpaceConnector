/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial Implementation
 *
 */

package org.eclipse.dataspaceconnector.ids.transform;

import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceCatalog;
import de.fraunhofer.iais.eis.ResourceCatalogBuilder;
import org.eclipse.dataspaceconnector.ids.spi.IdsIdParser;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.transform.IdsTypeTransformer;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerContext;
import org.eclipse.dataspaceconnector.ids.spi.types.DataCatalog;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class DataCatalogToIdsResourceCatalogTransformer implements IdsTypeTransformer<DataCatalog, ResourceCatalog> {

    @Override
    public Class<DataCatalog> getInputType() {
        return DataCatalog.class;
    }

    @Override
    public Class<ResourceCatalog> getOutputType() {
        return ResourceCatalog.class;
    }

    @Override
    public @Nullable ResourceCatalog transform(DataCatalog object, TransformerContext context) {
        Objects.requireNonNull(context);
        if (object == null) {
            return null;
        }

        ResourceCatalogBuilder builder;
        String catalogId = object.getId();
        if (catalogId != null) {
            URI catalogIdUri = URI.create(String.join(
                    IdsIdParser.DELIMITER,
                    IdsIdParser.SCHEME,
                    IdsType.CATALOG.getValue(),
                    catalogId));
            builder = new ResourceCatalogBuilder(catalogIdUri);
        } else {
            builder = new ResourceCatalogBuilder();
        }

        List<Resource> resources = new LinkedList<>();
        List<Asset> assets = object.getAssets();
        if (assets != null) {
            for (Asset asset : assets) {
                Resource resource = context.transform(asset, Resource.class);
                if (resource != null) {
                    resources.add(resource);
                }
            }
        }

        builder._offeredResource_(new ArrayList<>(resources));

        return builder.build();
    }
}
