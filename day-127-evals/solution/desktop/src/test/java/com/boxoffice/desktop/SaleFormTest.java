package com.boxoffice.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Money;
import com.boxoffice.core.TicketType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleFormTest {

    private static final TicketType REGULAR = new TicketType("REG", "Regular", Money.ofKes(1_500), 500);
    private static final TicketType VIP = new TicketType("VIP", "VIP", Money.ofKes(5_000), 50);

    private final SaleForm form = new SaleForm(List.of(REGULAR, VIP));

    @Test
    void anEmptyFormAsksForATicketTypeFirst() {
        assertThat(form.problem().get()).isEqualTo("Pick a ticket type: F1 Regular, F2 VIP");
        assertThat(form.ready().get()).isFalse();
    }

    @Test
    void functionKeysPickTheTypeInOrder() {
        form.pick(2);
        assertThat(form.typeProperty().get()).isEqualTo(VIP);

        form.pick(1);
        assertThat(form.typeProperty().get()).isEqualTo(REGULAR);
    }

    @Test
    void aFunctionKeyWithNoTypeDoesNothing() {
        form.pick(1);

        form.pick(5);

        assertThat(form.typeProperty().get()).isEqualTo(REGULAR);
    }

    @Test
    void thenAsksForTheBuyersName() {
        form.pick(1);
        form.buyerProperty().set("   ");

        assertThat(form.problem().get()).isEqualTo("Type the buyer's name");
    }

    @Test
    void isReadyWithATypeAndAName() {
        form.pick(1);
        form.buyerProperty().set("Otieno");

        assertThat(form.problem().get()).isEmpty();
        assertThat(form.ready().get()).isTrue();
    }

    @Test
    void thePhoneIsOptional() {
        form.pick(1);
        form.buyerProperty().set("Otieno");
        form.phoneProperty().set("");

        assertThat(form.ready().get()).isTrue();
    }

    @Test
    void aPhoneThatIsTypedHasToLookRight() {
        form.pick(1);
        form.buyerProperty().set("Otieno");

        form.phoneProperty().set("0712 34");
        assertThat(form.problem().get()).isEqualTo("Phone should look like 0712 345 678");
        assertThat(form.ready().get()).isFalse();

        form.phoneProperty().set("0712 345 678");
        assertThat(form.ready().get()).isTrue();
    }

    @Test
    void readyChangesByItselfAsTheFormIsFilledIn() {
        StringBuilder seen = new StringBuilder();
        form.ready().addListener((property, before, after) -> seen.append(after).append(';'));

        form.pick(1);
        form.buyerProperty().set("Otieno");
        form.phoneProperty().set("07");
        form.phoneProperty().set("0712 345 678");

        assertThat(seen.toString()).isEqualTo("true;false;true;");
    }

    @Test
    void clearingEmptiesTheNameAndPhoneButKeepsTheType() {
        form.pick(2);
        form.buyerProperty().set("Wanjiku");
        form.phoneProperty().set("0712 345 678");

        form.clear();

        assertThat(form.buyerProperty().get()).isEmpty();
        assertThat(form.phoneProperty().get()).isEmpty();
        assertThat(form.typeProperty().get()).isEqualTo(VIP);
    }

    @Test
    void clearsAfterTheSaleItWasFilledInFor() {
        form.pick(1);
        form.buyerProperty().set("Otieno");

        form.clearIfStill("Otieno", "");

        assertThat(form.buyerProperty().get()).isEmpty();
    }

    @Test
    void keepsTheNextNameTypedWhileTheLastSaleWasSaving() {
        form.pick(1);
        form.buyerProperty().set("Achieng");

        form.clearIfStill("Otieno", "");

        assertThat(form.buyerProperty().get()).isEqualTo("Achieng");
    }
}
