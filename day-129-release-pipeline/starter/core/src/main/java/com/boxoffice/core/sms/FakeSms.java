package com.boxoffice.core.sms;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pretends to send texts. It can be slow on purpose (like a real gateway over
 * venue Wi-Fi), refuse some numbers, and it counts how many sends were running
 * at the same moment, so a test can check the sender's limit.
 */
public final class FakeSms implements SmsGateway {

    /** One message the fake was asked to send. */
    public record Sent(String phone, String message) {
    }

    private final Duration delay;
    private final Set<String> refused = ConcurrentHashMap.newKeySet();
    private final List<Sent> sent = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger mostAtOnce = new AtomicInteger();

    public FakeSms(Duration delay) {
        this.delay = delay;
    }

    /** Every send to this number fails, like a phone that's switched off. */
    public FakeSms refusing(String phone) {
        refused.add(phone);
        return this;
    }

    @Override
    public SmsResult send(String phone, String message) {
        int now = running.incrementAndGet();
        mostAtOnce.accumulateAndGet(now, Math::max);
        try {
            Thread.sleep(delay);
            if (refused.contains(phone)) {
                return SmsResult.failed(phone, "Phone unreachable");
            }
            sent.add(new Sent(phone, message));
            return SmsResult.sent(phone);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SmsResult.failed(phone, "Interrupted");
        } finally {
            running.decrementAndGet();
        }
    }

    public List<Sent> sent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    /** The most sends that were ever running at the same moment. */
    public int mostAtOnce() {
        return mostAtOnce.get();
    }
}
