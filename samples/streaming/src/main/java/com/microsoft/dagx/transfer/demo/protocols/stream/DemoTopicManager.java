package com.microsoft.dagx.transfer.demo.protocols.stream;

import com.microsoft.dagx.spi.DagxException;
import com.microsoft.dagx.spi.monitor.Monitor;
import com.microsoft.dagx.transfer.demo.protocols.common.AbstractQueuedProvisioner;
import com.microsoft.dagx.transfer.demo.protocols.common.DataDestination;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.ConnectionResult;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.StreamObserver;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.Subscription;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.SubscriptionResult;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.TopicManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Implements simple, in-memory topics with pub/sub semantics.
 */
public class DemoTopicManager extends AbstractQueuedProvisioner implements TopicManager {
    private Monitor monitor;

    private Map<String, TopicContainer> containerCache = new ConcurrentHashMap<>();
    private Map<String, StreamObserver> observers = new ConcurrentHashMap<>();

    public DemoTopicManager(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public ConnectionResult connect(String topicName, String accessToken) {
        var container = containerCache.get(topicName);
        if (container == null) {
            return new ConnectionResult("Topic not found: " + topicName);
        } else if (!container.destination.getAccessToken().equals(accessToken)) {
            return new ConnectionResult("Not authorized");
        }
        return new ConnectionResult(data -> {
            container.containers.values().forEach(c -> c.accept(data));
            observers.values().forEach(o -> o.onPublish(topicName, data));
        });
    }

    @Override
    public SubscriptionResult subscribe(String topicName, String accessToken, Consumer<byte[]> consumer) {
        Objects.requireNonNull(topicName);
        var subscriptionId = UUID.randomUUID().toString();
        var container = getContainer(topicName);
        if (!container.destination.getAccessToken().equals(accessToken)) {
            return new SubscriptionResult("Invalid key");
        }
        container.containers.put(subscriptionId, consumer);
        return new SubscriptionResult(new Subscription(topicName, subscriptionId));
    }

    @Override
    public void unsubscribe(Subscription subscription) {
        getContainer(subscription.getTopicName()).containers.remove(subscription.getSubscriptionId());
    }

    @Override
    public String registerObserver(StreamObserver observer) {
        var id = UUID.randomUUID().toString();
        observers.put(id, observer);
        return id;
    }

    @Override
    public void unRegisterObserver(String id) {
        observers.remove(id);
    }

    @Override
    protected void onEntry(QueueEntry entry) {
        var token = UUID.randomUUID().toString();
        var destination = new DataDestination(entry.getDestinationName(), token);
        var container = new TopicContainer(destination);
        containerCache.put(entry.getDestinationName(), container);

        monitor.info("Provisioned a demo destination topic");

        entry.getFuture().complete(destination);

        containerCache.put(entry.getDestinationName(), new TopicContainer(destination));
        entry.getFuture().complete(destination);
    }

    @NotNull
    private DemoTopicManager.TopicContainer getContainer(String topicName) {
        var consumers = containerCache.get(topicName);
        if (consumers == null) {
            throw new DagxException("Invalid topic: " + topicName);
        }
        return consumers;
    }

    private static class TopicContainer {
        DataDestination destination;
        Map<String, Consumer<byte[]>> containers = new ConcurrentHashMap<>();

        public TopicContainer(DataDestination destination) {
            this.destination = destination;
        }
    }
}
