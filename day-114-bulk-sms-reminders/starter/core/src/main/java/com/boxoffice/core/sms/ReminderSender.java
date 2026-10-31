package com.boxoffice.core.sms;

import com.boxoffice.core.Event;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.Ticket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Texts every buyer who left a phone number, the afternoon before the event.
 *
 * <p>Each text spends most of its time waiting for the network. Sent one after
 * another, 500 texts at half a second each is four minutes; sent all at once on
 * virtual threads, it's a few seconds. Virtual threads are cheap enough to have
 * one per text. A Semaphore keeps the number running at once under the
 * gateway's limit, so we don't get blocked for flooding it.
 */
public final class ReminderSender {

    private final SmsGateway gateway;
    private final int maxAtOnce;

    public ReminderSender(SmsGateway gateway, int maxAtOnce) {
        this.gateway = gateway;
        this.maxAtOnce = maxAtOnce;
    }

    /** One text per phone number: a family that shares a phone gets one text listing all its tickets. */
    public static Map<String, String> messages(Event event, List<Ticket> tickets) {
        Map<String, List<String>> codesByPhone = new LinkedHashMap<>();
        for (Ticket ticket : tickets) {
            if (!ticket.phone().isEmpty()) {
                codesByPhone.computeIfAbsent(ticket.phone(), phone -> new ArrayList<>()).add(ticket.code());
            }
        }
        Map<String, String> messages = new LinkedHashMap<>();
        String when = NairobiTime.format(event.startsAt().toInstant());
        codesByPhone.forEach((phone, codes) -> messages.put(phone,
                event.name() + ", " + when + ". " + (codes.size() == 1 ? "Your ticket: " : "Your tickets: ")
                        + String.join(", ", codes) + ". Show the QR code at the gate."));
        return messages;
    }

    /** Sends every message and returns one result per phone, in the same order. Never throws for one bad send. */
    public List<SmsResult> sendAll(Map<String, String> messages) {
        Semaphore permits = new Semaphore(maxAtOnce);
        List<Future<SmsResult>> pending = new ArrayList<>();
        // TODO: right now this sends one text after another, so 100 slow texts take ages. Instead:
        //   1. open try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) { ... }
        //      (close() at the end of the try waits until every text has been tried)
        //   2. for each phone and message, pending.add(threads.submit(() -> { ... }))
        //   3. inside: permits.acquire(); then try { return gateway.send(phone, message); }
        //      catch (RuntimeException e) { return SmsResult.failed(phone, e.getMessage()); }
        //      finally { permits.release(); }   (always give the permit back, even when a send fails)
        // Then delete the loop below.
        messages.forEach((phone, message) -> pending.add(
                java.util.concurrent.CompletableFuture.completedFuture(gateway.send(phone, message))));
        List<SmsResult> results = new ArrayList<>();
        for (Future<SmsResult> result : pending) {
            try {
                results.add(result.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException("A send failed unexpectedly", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while sending", e);
            }
        }
        return results;
    }
}
