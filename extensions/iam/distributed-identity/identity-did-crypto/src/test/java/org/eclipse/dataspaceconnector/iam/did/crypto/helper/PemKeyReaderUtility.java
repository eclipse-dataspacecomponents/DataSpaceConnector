package org.eclipse.dataspaceconnector.iam.did.crypto.helper;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This is not an actual unit test, it is merely a utility to read pem files and get the parameters
 */
public class PemKeyReaderUtility {

    @Test
    @Disabled
    void readPemFile() {
        var jwk1 = parsePemAsJwk("/home/paul/dev/ion-demo/keys2/consumer-public.pem");
        var jwk2 = parsePemAsJwk("/home/paul/dev/ion-demo/keys2/verifier-public.pem");
        var jwk3 = parsePemAsJwk("/home/paul/dev/ion-demo/keys2/provider-public.pem");
        System.out.println("Public keys: ");
        System.out.printf("consumer: %s%n", jwk1);
        System.out.printf("verifier: %s%n", jwk2);
        System.out.printf("provider: %s%n", jwk3);
    }


    private JWK parsePemAsJwk(String resourceName) {

        try {
            var pemContents = Files.readString(Path.of(resourceName));
            return ECKey.parseFromPEMEncodedObjects(pemContents);

        } catch (JOSEException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
