package com.boxoffice.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SalesToolsTest {

    // Nairobi is three hours ahead of UTC: 17:00Z is 20:00 at the racecourse.
    private static Clock at(String nairobiTime, int day) {
        int hour = Integer.parseInt(nairobiTime.substring(0, 2)) - 3;
        String utc = "2026-11-%02dT%02d:%s:00Z".formatted(hour < 0 ? day - 1 : day, (hour + 24) % 24,
                nairobiTime.substring(3));
        return Clock.fixed(Instant.parse(utc), ZoneOffset.UTC);
    }

    @TempDir
    Path folder;

    private TicketStore store;
    private SalesTools tools;

    @BeforeEach
    void openTheGate() {
        store = TicketStore.open(folder.resolve("gate.db"));
        tools = new SalesTools(store, SampleEvent.ensureIn(store));
    }

    @AfterEach
    void close() {
        store.close();
    }

    private Gate gateAt(String nairobiTime, int day) {
        return new Gate(store, SampleEvent.ID, at(nairobiTime, day));
    }

    private ToolResult run(String name, String input) {
        return tools.run(new ToolCall("call_1", name, input));
    }

    @Test
    void findsATicketWithoutGivingAwayTheBuyer() {
        SaleOutcome.Sold sold = (SaleOutcome.Sold) gateAt("21:05", 14).sell("VIP", "Wanjiku Wambui Kamau", "254712345678");
        String code = sold.ticket().code();
        gateAt("21:30", 14).checkIn(code);

        ToolResult result = run("find_ticket", "{\"code\":\"" + code.toLowerCase() + "\"}");

        assertThat(result).isEqualTo(new ToolResult("call_1",
                code + ": VIP, KES 5,000, sold Sat 14 Nov, 21:05 to Wanjiku K., phone ending 678. It came in at 21:30",
                false));
        assertThat(result.content()).doesNotContain("Kamau").doesNotContain("254712");
    }

    @Test
    void aGuestTicketThatHasNotComeIn() {
        SaleOutcome.Sold guest = (SaleOutcome.Sold) gateAt("19:00", 14).admitGuest("REG", "Kalonje");

        ToolResult result = run("find_ticket", "{\"code\":\"" + guest.ticket().code() + "\"}");

        assertThat(result.content()).isEqualTo(guest.ticket().code()
                + ": Regular, a guest ticket, sold Sat 14 Nov, 19:00 to Kalonje, no phone given. It hasn't come in yet");
    }

    @Test
    void anUnknownTicketIsAnAnswerNotAFailure() {
        ToolResult result = run("find_ticket", "{\"code\":\"VIP-0099\"}");

        assertThat(result).isEqualTo(new ToolResult("call_1", "No ticket VIP-0099 was sold for Kilele Nights", false));
    }

    @Test
    void aCodeThatCantBeATicketFails() {
        assertThat(run("find_ticket", "{\"code\":\"'; drop table tickets; --\"}"))
                .isEqualTo(new ToolResult("call_1", "code should be a ticket code like VIP-0001", true));
        assertThat(run("find_ticket", "{}").failed()).isTrue();
        assertThat(run("find_ticket", "{\"code\":42}").failed()).isTrue();
    }

    @Test
    void countsSalesInATimeWindow() {
        gateAt("19:59", 14).sell("REG", "Too early");
        gateAt("20:00", 14).sell("REG", "Otieno");
        gateAt("20:30", 14).sellGroup("VIP", "Kamau family", "", 2);
        gateAt("20:45", 14).admitGuest("REG", "Kalonje");
        gateAt("21:00", 14).sell("REG", "Too late");

        ToolResult result = run("sales_between", "{\"from\":\"20:00\",\"to\":\"21:00\"}");

        assertThat(result.content()).isEqualTo(
                "Between 20:00 and 21:00: Regular 1 paid and 1 guest, VIP 2 paid and 0 guests. Takings KES 11,500");
    }

    @Test
    void theSmallHoursAreAfterMidnight() {
        gateAt("01:30", 15).sell("REG", "Late arrival");

        ToolResult result = run("sales_between", "{\"from\":\"23:00\",\"to\":\"02:00\"}");

        assertThat(result.content()).startsWith("Between 23:00 and 02:00: Regular 1 paid");
    }

    @Test
    void timesMustBeTimesInOrder() {
        assertThat(run("sales_between", "{\"from\":\"8pm\",\"to\":\"21:00\"}"))
                .isEqualTo(new ToolResult("call_1", "from and to should be times like 20:00", true));
        assertThat(run("sales_between", "{\"from\":\"21:00\",\"to\":\"20:00\"}"))
                .isEqualTo(new ToolResult("call_1", "from should be before to", true));
    }

    @Test
    void countsWhoCameIn() {
        SaleOutcome.Sold vip = (SaleOutcome.Sold) gateAt("20:00", 14).sell("VIP", "Wanjiku");
        SaleOutcome.Sold reg = (SaleOutcome.Sold) gateAt("20:01", 14).sell("REG", "Otieno");
        gateAt("20:02", 14).sell("REG", "Achieng");
        gateAt("21:10", 14).checkIn(vip.ticket().code());
        gateAt("21:40", 14).checkIn(reg.ticket().code());

        assertThat(run("check_ins", "{}").content())
                .isEqualTo("Came in so far: Regular 1 of 2, VIP 1 of 1. The last came in at 21:40");
        assertThat(run("check_ins", "{\"type\":\"vip\"}").content())
                .isEqualTo("Came in so far: VIP 1 of 1. The last came in at 21:10");
    }

    @Test
    void anEmptyGateHasNobodyIn() {
        assertThat(run("check_ins", "").content()).isEqualTo("Came in so far: Regular 0 of 0, VIP 0 of 0. Nobody has come in yet");
    }

    @Test
    void anUnknownTypeSaysWhatThereIs() {
        assertThat(run("check_ins", "{\"type\":\"VVIP\"}"))
                .isEqualTo(new ToolResult("call_1", "There's no ticket type VVIP. Kilele Nights sells REG, VIP", true));
    }

    @Test
    void brokenInputAndUnknownToolsFail() {
        assertThat(run("find_ticket", "{\"code\": \"VIP-")).isEqualTo(
                new ToolResult("call_1", "The input wasn't valid JSON", true));
        assertThat(run("find_ticket", "[\"VIP-0001\"]")).isEqualTo(
                new ToolResult("call_1", "The input should be a JSON object", true));
        assertThat(run("sell_ticket", "{}")).isEqualTo(
                new ToolResult("call_1", "There's no tool called sell_ticket", true));
    }

    @Test
    void namesAreShortened() {
        assertThat(SalesTools.shortName("Wanjiku Kamau")).isEqualTo("Wanjiku K.");
        assertThat(SalesTools.shortName("  Otieno  ")).isEqualTo("Otieno");
    }
}
