package org.eclipse.dataspaceconnector.catalog.spi;

public interface Crawler extends Runnable {
    String FEATURE = "edc:catalog:cache:crawler";

    void addAdapter(ProtocolAdapter adapter);
}
