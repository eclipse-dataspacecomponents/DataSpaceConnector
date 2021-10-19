package org.eclipse.dataspaceconnector.ion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dataspaceconnector.iam.did.crypto.key.KeyPairFactory;
import org.eclipse.dataspaceconnector.ion.spi.request.IonRequestFactory;
import org.eclipse.dataspaceconnector.ion.spi.request.PublicKeyDescriptor;
import org.eclipse.dataspaceconnector.ion.spi.request.ServiceDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IonClientImplTest {

    private static final String DID_URL_TO_RESOLVE = "did:ion:EiDfkaPHt8Yojnh15O7egrj5pA9tTefh_SYtbhF1-XyAeA";


    private DefaultIonClient client;

    @BeforeEach
    void setup() {
        client = new DefaultIonClient(new ObjectMapper());
    }

    @Test
    void resolve() {
        var result = client.resolve(DID_URL_TO_RESOLVE);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(DID_URL_TO_RESOLVE);
    }


    @Test
    void resolve_notFound() {
        assertThatThrownBy(() -> client.resolve("did:ion:notexist")).isInstanceOf(IonRequestException.class)
                .hasMessageContaining("404");
    }

    @Test
    void resolve_wrongPrefix() {
        assertThatThrownBy(() -> client.resolve("did:ion:foobar:notexist")).isInstanceOf(IonRequestException.class)
                .hasMessageContaining("400");
    }

    @Test
    void submitAnchorRequest() {
        var pair = KeyPairFactory.generateKeyPair();
        var pkd = PublicKeyDescriptor.Builder.create()
                .id("key-1")
                .type("EcdsaSecp256k1VerificationKey2019")
                .publicKeyJwk(Map.of("publicKeyJwk", pair.toPublicJWK().toJSONString())).build();

        var sd = Collections.singletonList(ServiceDescriptor.Builder.create().id("idhub-url")
                .type("IdentityHubUrl").serviceEndpoint("https://my.identity.url").build());

        var content = new HashMap<String, Object>();
        content.put("publicKeys", Collections.singletonList(pkd));
        content.put("services", sd);
        var createDidRequest = IonRequestFactory.createCreateRequest(KeyPairFactory.generateKeyPair().toPublicJWK(), KeyPairFactory.generateKeyPair().toPublicJWK(), content);
        assertThat(createDidRequest.getDidUri()).isNotNull();
        assertThat(createDidRequest.getSuffix()).isNotNull();
        assertThat(createDidRequest.getDidUri()).endsWith(createDidRequest.getSuffix());

        //TODO: commented out because anchoring does not yet work due to rate limiting

        //        var didDoc = client.submit(createDidRequest);
        //        assertThat(didDoc).isNotNull();
        //        assertThat(didDoc.getId()).isNotNull();
    }
}
