package com.boxoffice.core.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Money;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.Ticket;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReminderSenderTest {

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");

    private static Ticket ticket(String code, String phone) {
        return new Ticket(code, code.substring(0, 3), "Someone", phone, Money.ofKes(1_500), NINE_PM);
    }

    private static Map<String, String> messagesTo(int howMany) {
        Map<String, String> messages = new LinkedHashMap<>();
        for (int i = 0; i < howMany; i++) {
            messages.put("2547%08d".formatted(i), "See you tonight");
        }
        return messages;
    }

    @Test
    void writesOneMessagePerPhone() {
        Map<String, String> messages = ReminderSender.messages(SampleEvent.kileleNights(), List.of(
                ticket("VIP-0001", "254712345678"),
                ticket("REG-0001", "254700000001"),
                ticket("REG-0002", "254700000001")));

        assertThat(messages).containsExactly(
                Map.entry("254712345678",
                        "Kilele Nights, Sat 14 Nov, 18:00. Your ticket: VIP-0001. Show the QR code at the gate."),
                Map.entry("254700000001",
                        "Kilele Nights, Sat 14 Nov, 18:00. Your tickets: REG-0001, REG-0002. Show the QR code at the gate."));
    }

    @Test
    void skipsTicketsWithNoPhone() {
        assertThat(ReminderSender.messages(SampleEvent.kileleNights(), List.of(ticket("REG-0001", "")))).isEmpty();
    }

    @Test
    void sendsEveryMessage() {
        FakeSms sms = new FakeSms(Duration.ZERO);

        List<SmsResult> results = new ReminderSender(sms, 10).sendAll(messagesTo(25));

        assertThat(results).hasSize(25).allMatch(SmsResult::sent);
        assertThat(sms.sent()).hasSize(25);
    }

    @Test
    void sendsAtTheSameTimeInsteadOfOneByOne() {
        // 100 texts at 200 ms each would take 20 seconds one by one.
        FakeSms slowSms = new FakeSms(Duration.ofMillis(200));

        long start = System.nanoTime();
        new ReminderSender(slowSms, 100).sendAll(messagesTo(100));
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(took).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void neverRunsMoreThanTheLimitAtOnce() {
        FakeSms sms = new FakeSms(Duration.ofMillis(50));

        new ReminderSender(sms, 5).sendAll(messagesTo(40));

        assertThat(sms.mostAtOnce()).isBetween(1, 5);
        assertThat(sms.sent()).hasSize(40);
    }

    @Test
    void oneBadNumberDoesNotStopTheOthers() {
        FakeSms sms = new FakeSms(Duration.ZERO).refusing("254700000001");

        List<SmsResult> results = new ReminderSender(sms, 10).sendAll(messagesTo(3));

        assertThat(results).extracting(SmsResult::sent).containsExactly(true, false, true);
        assertThat(results.get(1).detail()).isEqualTo("Phone unreachable");
    }

    @Test
    void aGatewayThatThrowsCountsAsAFailedSend() {
        SmsGateway broken = (phone, message) -> {
            throw new IllegalStateException("Gateway exploded");
        };

        List<SmsResult> results = new ReminderSender(broken, 10).sendAll(messagesTo(2));

        assertThat(results).extracting(SmsResult::detail).containsOnly("Gateway exploded");
    }

    @Test
    void givesTheResultsInTheSameOrderAsThePhones() {
        FakeSms sms = new FakeSms(Duration.ofMillis(10));

        List<SmsResult> results = new ReminderSender(sms, 50).sendAll(messagesTo(30));

        assertThat(results).extracting(SmsResult::phone).containsExactlyElementsOf(messagesTo(30).keySet());
    }
}
