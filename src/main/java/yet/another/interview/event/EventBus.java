package yet.another.interview.event;

import yet.another.interview.model.OrderCreated;
import yet.another.interview.model.SubscriptionToken;

import java.util.function.Consumer;

public interface EventBus extends AutoCloseable {
    SubscriptionToken subscribe(Consumer<OrderCreated> handler);
    void unsubscribe(SubscriptionToken token);
    void publish(OrderCreated event);
}
