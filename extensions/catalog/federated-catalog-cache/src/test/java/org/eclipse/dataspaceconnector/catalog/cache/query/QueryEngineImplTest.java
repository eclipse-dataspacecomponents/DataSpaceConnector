package org.eclipse.dataspaceconnector.catalog.cache.query;

import org.eclipse.dataspaceconnector.catalog.spi.CacheQueryAdapterRegistry;
import org.eclipse.dataspaceconnector.catalog.spi.QueryEngine;
import org.eclipse.dataspaceconnector.catalog.spi.QueryResponse;
import org.eclipse.dataspaceconnector.catalog.spi.model.FederatedCatalogCacheQuery;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractOffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryEngineImplTest {

    private static final ContractOffer ASSET_ABC = ContractOffer.Builder.newInstance().id("ABC").build();
    private static final ContractOffer ASSET_DEF = ContractOffer.Builder.newInstance().id("DEF").build();
    private static final ContractOffer ASSET_XYZ = ContractOffer.Builder.newInstance().id("XYZ").build();

    @Test
    void getCatalog() {
        CacheQueryAdapterRegistry registry = mock(CacheQueryAdapterRegistry.class);

        when(registry.executeQuery(any())).thenReturn(QueryResponse.ok(List.of(ASSET_ABC, ASSET_DEF, ASSET_XYZ)));

        QueryEngine queryEngine = new QueryEngineImpl(registry);

        QueryResponse catalog = queryEngine.getCatalog(FederatedCatalogCacheQuery.Builder.newInstance().build());
        assertThat(catalog.getStatus()).isEqualTo(QueryResponse.Status.ACCEPTED);
        assertThat(catalog.getErrors()).isEmpty();
        assertThat(catalog.getOffers()).containsExactlyInAnyOrder(ASSET_ABC, ASSET_DEF, ASSET_XYZ);

        verify(registry).executeQuery(any());
    }

    @Test
    void getCatalog_withErrors() {
        CacheQueryAdapterRegistry registry = mock(CacheQueryAdapterRegistry.class);

        when(registry.executeQuery(any())).thenReturn(QueryResponse.Builder.newInstance()
                .offers(List.of(ASSET_ABC, ASSET_DEF, ASSET_XYZ))
                .error("some error")
                .build());

        QueryEngine queryEngine = new QueryEngineImpl(registry);

        QueryResponse catalog = queryEngine.getCatalog(FederatedCatalogCacheQuery.Builder.newInstance().build());
        assertThat(catalog.getStatus()).isEqualTo(QueryResponse.Status.ACCEPTED);
        assertThat(catalog.getErrors()).hasSize(1).containsExactly("some error");
        assertThat(catalog.getOffers()).containsExactlyInAnyOrder(ASSET_ABC, ASSET_DEF, ASSET_XYZ);
        verify(registry).executeQuery(any());
    }

    @Test
    void getCatalog_notAccepted() {
        CacheQueryAdapterRegistry registry = mock(CacheQueryAdapterRegistry.class);

        when(registry.executeQuery(any())).thenReturn(QueryResponse.Builder.newInstance()
                .status(QueryResponse.Status.NO_ADAPTER_FOUND)
                .error("no adapter was found for that query")
                .build());

        QueryEngine queryEngine = new QueryEngineImpl(registry);

        QueryResponse catalog = queryEngine.getCatalog(FederatedCatalogCacheQuery.Builder.newInstance().build());
        assertThat(catalog.getStatus()).isEqualTo(QueryResponse.Status.NO_ADAPTER_FOUND);
        assertThat(catalog.getErrors()).hasSize(1);
        assertThat(catalog.getOffers()).isEmpty();
        verify(registry).executeQuery(any());
    }
}