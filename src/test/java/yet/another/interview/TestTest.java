package yet.another.interview;


import org.junit.jupiter.api.Test;
import yet.another.interview.event.EventBus;
import yet.another.interview.event.OrderEventBus;
import yet.another.interview.model.OrderCreated;
import yet.another.interview.model.SubscriptionToken;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTest {
    @Test
    public void testEventDeliveryWithToken() throws InterruptedException {
        EventBus eventBus = new OrderEventBus(Executors.newSingleThreadExecutor());

        AtomicInteger orderCount = new AtomicInteger();
        AtomicReference<String> lastOrderId = new AtomicReference<>();

        SubscriptionToken token = eventBus.subscribe(order -> {
            orderCount.incrementAndGet();
            lastOrderId.set(order.orderId);
        });

        eventBus.publish(new OrderCreated("ORDER-123"));
        Thread.sleep(100);

        assertEquals(1, orderCount.get());
        assertEquals("ORDER-123", lastOrderId.get());
    }

    @Test
    public void testEventDeliveryInConcurrency() throws InterruptedException {
        EventBus eventBus = new OrderEventBus(Executors.newFixedThreadPool(10));

        AtomicInteger orderCount = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch countDownLatch = new CountDownLatch(10);
        CountDownLatch countDownLatchForHandlers = new CountDownLatch(100);

        var keys = IntStream.range(0, 10).mapToObj(n ->
            eventBus.subscribe(order -> {
                orderCount.incrementAndGet();
                countDownLatchForHandlers.countDown();
            })
        ).toList();

        IntStream.range(0, 10).forEach(n ->
            executorService.execute(() -> {
                eventBus.publish(new OrderCreated("ORDER-123"));
                countDownLatch.countDown();
            })
        );
        countDownLatch.await();
        countDownLatchForHandlers.await();

        assertEquals(100, orderCount.get());
    }


    @Test
    public void testEventDeliveryUnsubscribeRuntime() throws InterruptedException {
        int countHandlers = 2;
        int countMessagesForSend = 5;
        int countHandlersSubscribeAll = 15;
        int countHandlersSingleSubscriber = 5;

        EventBus eventBus = new OrderEventBus(Executors.newFixedThreadPool(10));

        AtomicInteger orderCount = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch sendersFirst = new CountDownLatch(countMessagesForSend);
        CountDownLatch countDownLatchForHandlers = new CountDownLatch(countHandlersSubscribeAll);

        var keys = IntStream.range(0, countHandlers).mapToObj(n ->
                eventBus.subscribe(order -> {
                    orderCount.incrementAndGet();
                    countDownLatchForHandlers.countDown();
                })
        ).toList();

        IntStream.range(0, countMessagesForSend).forEach(n ->
                executorService.execute(() -> {
                    eventBus.publish(new OrderCreated("ORDER-123"));
                    sendersFirst.countDown();
                })
        );
        sendersFirst.await();

        eventBus.unsubscribe(keys.stream().findFirst().get());

        CountDownLatch sendersSecond = new CountDownLatch(countHandlersSingleSubscriber);

        IntStream.range(0, countMessagesForSend).forEach(n ->
                executorService.execute(() -> {
                    eventBus.publish(new OrderCreated("ORDER-123"));
                    sendersSecond.countDown();
                })
        );
        sendersSecond.await();
        countDownLatchForHandlers.await();

        assertEquals(countHandlersSubscribeAll, orderCount.get());
    }
}
