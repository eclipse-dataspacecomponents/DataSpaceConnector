package org.eclipse.dataspaceconnector.iam.did.spi.document;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Service {
    private String id;
    private String type;
    private String serviceEndpoint;

    public Service() {
        // needed for JSON Deserialization
    }

    public Service(String id, String type, String serviceEndpoint) {
        this.id = id;
        this.type = type;
        this.serviceEndpoint = serviceEndpoint;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("serviceEndpoint")
    public String getServiceEndpoint() {
        return serviceEndpoint;
    }

    public void setServiceEndpoint(String serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
    }
}
