package org.eclipse.dataspaceconnector.metadata.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dataspaceconnector.spi.asset.AssetSelectorExpression;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataAddress;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.niceMock;

class InMemoryAssetIndexTest {
    private InMemoryAssetIndex index;


    @BeforeEach
    void setUp() {
        index = new InMemoryAssetIndex(new CriterionToPredicateConverter());
    }

    @Test
    void queryAssets() {
        var testAsset = createAsset("foobar");
        index.insert(testAsset, niceMock(DataAddress.class));
        var assets = index.queryAssets(AssetSelectorExpression.Builder.newInstance().whenEquals("name", "foobar").build());
        assertThat(assets).hasSize(1).containsExactly(testAsset);
    }

    @Test
    void queryAssets_notFound() {
        var testAsset = createAsset("foobar");
        index.insert(testAsset, niceMock(DataAddress.class));
        var assets = index.queryAssets(AssetSelectorExpression.Builder.newInstance().whenEquals("name", "barbaz").build());
        assertThat(assets).isEmpty();
    }

    @Test
    void queryAssets_fieldNull() {
        var testAsset = createAsset("foobar");
        index.insert(testAsset, niceMock(DataAddress.class));
        var assets = index.queryAssets(AssetSelectorExpression.Builder.newInstance().whenEquals("description", "barbaz").build());
        assertThat(assets).isEmpty();
    }

    @Test
    void queryAssets_multipleFound() {
        var testAsset1 = createAsset("foobar");
        var testAsset2 = createAsset("barbaz");
        var testAsset3 = createAsset("barbaz");
        index.insert(testAsset1, niceMock(DataAddress.class));
        index.insert(testAsset2, niceMock(DataAddress.class));
        index.insert(testAsset3, niceMock(DataAddress.class));
        var assets = index.queryAssets(AssetSelectorExpression.Builder.newInstance()
                .whenEquals("name", "barbaz")
                .whenEquals("version", "1")
                .build());
        assertThat(assets).hasSize(2).containsExactlyInAnyOrder(testAsset2, testAsset3);
    }

    @Test
    void queryAssets_noExpression_shouldReturnEmpty() {
        var result = index.queryAssets(AssetSelectorExpression.Builder.newInstance().build());
        assertThat(result).isEmpty();
    }

    @Test
    void queryAssets_selectAll_shouldReturnAll() {
        var testAsset1 = createAsset("barbaz");
        index.insert(testAsset1, niceMock(DataAddress.class));

        var testAsset2 = createAsset("foobar");
        index.insert(testAsset2, niceMock(DataAddress.class));

        assertThat(index.queryAssets(AssetSelectorExpression.SELECT_ALL)).containsExactlyInAnyOrder(testAsset1, testAsset2);
    }

    @Test
    void findById() {
        String id = UUID.randomUUID().toString();
        var testAsset = createAsset("barbaz", id);
        index.insert(testAsset, niceMock(DataAddress.class));

        assertThat(index.findById(id)).isNotNull().isEqualTo(testAsset);
    }


    @Test
    void findById_notfound() {
        String id = UUID.randomUUID().toString();
        var testAsset = createAsset("foobar", id);
        index.insert(testAsset, niceMock(DataAddress.class));

        assertThat(index.findById("not-exist")).isNull();
    }


    @NotNull
    private Asset createAsset(String name) {
        return createAsset(name, UUID.randomUUID().toString());
    }

    private Asset createAsset(String name, String id) {
        return Asset.Builder.newInstance().id(id).name(name).version("1").build();
    }

    private DataAddress createDataAddress(Asset asset) {
        return DataAddress.Builder.newInstance()
                .type("test-asset")
                .keyName("test-keyname")
                .properties(flatten(asset))
                .build();
    }

    private Map<String, ?> flatten(Object object) {

        try {
            var om = new ObjectMapper();
            om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            var json = om.writeValueAsString(object);
            return om.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
