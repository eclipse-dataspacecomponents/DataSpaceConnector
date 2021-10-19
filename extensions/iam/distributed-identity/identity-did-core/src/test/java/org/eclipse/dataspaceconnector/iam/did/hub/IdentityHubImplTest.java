package org.eclipse.dataspaceconnector.iam.did.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.easymock.EasyMock;
import org.eclipse.dataspaceconnector.iam.did.crypto.key.RsaPrivateKeyWrapper;
import org.eclipse.dataspaceconnector.iam.did.crypto.key.RsaPublicKeyWrapper;
import org.eclipse.dataspaceconnector.iam.did.hub.jwe.GenericJweReader;
import org.eclipse.dataspaceconnector.iam.did.hub.jwe.GenericJweWriter;
import org.eclipse.dataspaceconnector.iam.did.hub.jwe.WriteRequestWriter;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.IdentityHubStore;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.Commit;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.CommitQuery;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.CommitQueryRequest;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.CommitQueryResponse;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.HubObject;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.ObjectQuery;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.ObjectQueryRequest;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.ObjectQueryResponse;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.message.WriteResponse;
import org.eclipse.dataspaceconnector.iam.did.spi.key.PrivateKeyWrapper;
import org.eclipse.dataspaceconnector.iam.did.spi.key.PublicKeyWrapper;
import org.eclipse.dataspaceconnector.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.dataspaceconnector.iam.did.testfixtures.TemporaryKeyLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


class IdentityHubImplTest {
    private IdentityHubImpl hub;
    private IdentityHubStore store;
    private PrivateKeyWrapper privateKey;
    private PublicKeyWrapper publicKey;
    private ObjectMapper objectMapper;

    @Test
    void verifyStore() {
        store.write(EasyMock.isA(Commit.class));
        EasyMock.replay(store);

        var jwe = new WriteRequestWriter()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .objectMapper(objectMapper)
                .commitObject(Map.of("foo", "bar"))
                .kid("kid")
                .sub("sub")
                .context("Foo")
                .type("Bar").buildJwe();

        var responseJwe = hub.write(jwe);
        EasyMock.verify(store);

        // verify a revision was provided
        var response = new GenericJweReader().mapper(objectMapper).jwe(responseJwe).privateKey(privateKey).readType(WriteResponse.class);

        Assertions.assertNotNull(response.getRevisions().get(0));
    }

    @Test
    void verifyCommitQuery() {
        var commit = Commit.Builder.newInstance().context("foo").type("foo").objectId("123").iss("baz").sub("quux").payload("payload").alg("RSA256").kid("kid").build();

        var query = CommitQuery.Builder.newInstance().objectId("123").build();
        var request = CommitQueryRequest.Builder.newInstance().query(query).iss("123").aud("aud").sub("sub").build();
        EasyMock.expect(store.query(EasyMock.isA(CommitQuery.class))).andReturn(List.of(commit));
        EasyMock.replay(store);

        var jwe = new GenericJweWriter()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .objectMapper(objectMapper)
                .payload(request)
                .buildJwe();

        var responseJwe = hub.queryCommits(jwe);
        EasyMock.verify(store);

        // verify a revision was provided
        var response = new GenericJweReader().mapper(objectMapper).jwe(responseJwe).privateKey(privateKey).readType(CommitQueryResponse.class);

        Assertions.assertNotNull(response.getCommits().get(0));
    }

    @Test
    void verifyObjectQuery() {
        var hubObject = HubObject.Builder.newInstance().type("Foo").id("123").createdBy("test").sub("quux").build();

        var query = ObjectQuery.Builder.newInstance().type("Foo").build();
        var request = ObjectQueryRequest.Builder.newInstance().query(query).iss("123").aud("aud").sub("sub").build();
        EasyMock.expect(store.query(EasyMock.isA(ObjectQuery.class))).andReturn(List.of(hubObject));
        EasyMock.replay(store);

        var jwe = new GenericJweWriter()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .objectMapper(objectMapper)
                .payload(request)
                .buildJwe();

        var responseJwe = hub.queryObjects(jwe);
        EasyMock.verify(store);

        // verify a revision was provided
        var response = new GenericJweReader().mapper(objectMapper).jwe(responseJwe).privateKey(privateKey).readType(ObjectQueryResponse.class);

        Assertions.assertNotNull(response.getObjects().get(0));
    }

    @BeforeEach
    void setUp() throws Exception {
        var keys = TemporaryKeyLoader.loadKeys();
        privateKey = new RsaPrivateKeyWrapper(keys.toRSAPrivateKey());
        publicKey = new RsaPublicKeyWrapper(keys.toRSAPublicKey());
        store = EasyMock.createMock(IdentityHubStore.class);
        objectMapper = new ObjectMapper();
        hub = new IdentityHubImpl(store, () -> privateKey, did -> new DidPublicKeyResolver.Result(publicKey), objectMapper);
    }
}
