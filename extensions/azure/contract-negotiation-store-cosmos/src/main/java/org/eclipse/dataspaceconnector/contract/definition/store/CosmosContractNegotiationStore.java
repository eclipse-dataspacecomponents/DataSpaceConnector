package org.eclipse.dataspaceconnector.contract.definition.store;

import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.core.type.TypeReference;
import net.jodah.failsafe.RetryPolicy;
import org.eclipse.dataspaceconnector.contract.definition.store.model.ContractNegotiationDocument;
import org.eclipse.dataspaceconnector.cosmos.azure.CosmosDbApi;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.store.ContractNegotiationStore;
import org.eclipse.dataspaceconnector.spi.contract.offer.store.ContractDefinitionStore;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.ContractAgreement;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractNegotiation;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static net.jodah.failsafe.Failsafe.with;

/**
 * Implementation of the {@link ContractDefinitionStore} based on CosmosDB. This store implements simple write-through
 * caching mechanics: read operations (e.g. findAll) hit the cache, while write operations affect both the cache AND the
 * database.
 */
public class CosmosContractNegotiationStore implements ContractNegotiationStore {
    private final CosmosDbApi cosmosDbApi;
    private final TypeManager typeManager;
    private final RetryPolicy<Object> retryPolicy;
    private final AtomicReference<Map<String, ContractDefinition>> objectCache;
    private final String connectorId;
    private final ReentrantReadWriteLock lock; //used to synchronize write operations to the cache and the DB

    public CosmosContractNegotiationStore(CosmosDbApi cosmosDbApi, TypeManager typeManager, RetryPolicy<Object> retryPolicy, String connectorId) {
        this.cosmosDbApi = cosmosDbApi;
        this.typeManager = typeManager;
        this.retryPolicy = retryPolicy;
        this.connectorId = connectorId;
        objectCache = new AtomicReference<>(new ConcurrentHashMap<>());
        lock = new ReentrantReadWriteLock(true);
    }

    @Override
    public @Nullable ContractNegotiation find(String negotiationId) {
        var object = with(retryPolicy).get(() -> cosmosDbApi.queryItemById(negotiationId));
        return object != null ? toNegotiation(object) : null;
    }


    @Override
    public @Nullable ContractNegotiation findForCorrelationId(String correlationId) {
        final String query = "SELECT * FROM c WHERE (c.wrappedInstance.correlationId = @corrId)";
        SqlParameter param = new SqlParameter("@corrId", correlationId);
        var querySpec = new SqlQuerySpec(query, param);

        //todo: throw exception if more than 1 element?
        var objects = with(retryPolicy).get(() -> cosmosDbApi.queryItems(querySpec));
        return objects.findFirst().map(this::toNegotiation).orElse(null);
    }

    @Override
    public @Nullable ContractAgreement findContractAgreement(String contractId) {
        final String query = "SELECT * FROM c WHERE c.wrappedInstance.contractAgreement.id = @contractId";
        SqlParameter param = new SqlParameter("@contractId", contractId);

        var spec = new SqlQuerySpec(query, param);
        var objects = with(retryPolicy).get(() -> cosmosDbApi.queryItems(spec));
        return objects.findFirst().map(o -> toNegotiation(o).getContractAgreement()).orElse(null);
    }

    @Override
    public void save(ContractNegotiation negotiation) {
        cosmosDbApi.saveItem(new ContractNegotiationDocument(negotiation));
    }

    @Override
    public void delete(String negotiationId) {
        cosmosDbApi.deleteItem(negotiationId);
    }

    @Override
    public @NotNull List<ContractNegotiation> nextForState(int state, int max) {

        var partitionKey = String.valueOf(state);
        String rawJson = cosmosDbApi.invokeStoredProcedure("nextForState", partitionKey, state, max, connectorId);
        var typeRef = new TypeReference<List<Object>>() {
        };
        var list = typeManager.readValue(rawJson, typeRef);
        return list.stream().map(this::toNegotiation).collect(Collectors.toList());
    }

    private void storeInCache(ContractDefinition definition) {
        objectCache.get().put(definition.getId(), definition);
    }

    @NotNull
    private ContractNegotiationDocument convertToDocument(ContractNegotiation negotiation) {
        return new ContractNegotiationDocument(negotiation);
    }


    private ContractNegotiation toNegotiation(Object object) {
        var json = typeManager.writeValueAsString(object);
        return typeManager.readValue(json, ContractNegotiationDocument.class).getWrappedInstance();
    }
}
