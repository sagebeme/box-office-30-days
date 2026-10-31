package com.boxoffice.server.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StkCallbackTest {

    static final String PAID = """
            {"Body":{"stkCallback":{"MerchantRequestID":"29115-34620561-1",\
            "CheckoutRequestID":"ws_CO_191220191020363925","ResultCode":0,\
            "ResultDesc":"The service request is processed successfully.",\
            "CallbackMetadata":{"Item":[{"Name":"Amount","Value":3000.00},\
            {"Name":"MpesaReceiptNumber","Value":"QK12ABC3DE"},{"Name":"Balance"},\
            {"Name":"TransactionDate","Value":20261114183512},{"Name":"PhoneNumber","Value":254712345678}]}}}}""";

    @Test
    void aPaymentThatWentThrough() {
        StkCallback callback = StkCallback.parse(PAID);

        assertThat(callback.paid()).isTrue();
        assertThat(callback.checkoutRequestId()).isEqualTo("ws_CO_191220191020363925");
        assertThat(callback.amount()).contains(3_000L);
        assertThat(callback.receipt()).contains("QK12ABC3DE");
        assertThat(callback.phone()).contains("254712345678");
    }

    @Test
    void theItemsAreFoundByNameInAnyOrderAndAnyCase() {
        StkCallback callback = StkCallback.parse("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_1","ResultCode":0,"ResultDesc":"ok",\
                "CallbackMetadata":{"Item":[{"Name":"phonenumber","Value":254700111222},\
                {"Name":"MPESARECEIPTNUMBER","Value":"QK99"},{"Name":"amount","Value":1500}]}}}}""");

        assertThat(callback.amount()).contains(1_500L);
        assertThat(callback.receipt()).contains("QK99");
        assertThat(callback.phone()).contains("254700111222");
    }

    @Test
    void aCancelledPaymentHasNoMetadataAtAll() {
        StkCallback callback = StkCallback.parse("""
                {"Body":{"stkCallback":{"MerchantRequestID":"m","CheckoutRequestID":"ws_CO_2",\
                "ResultCode":1032,"ResultDesc":"Request cancelled by user"}}}""");

        assertThat(callback.paid()).isFalse();
        assertThat(callback.resultCode()).isEqualTo(StkCallback.CANCELLED_BY_USER);
        assertThat(callback.resultDesc()).isEqualTo("Request cancelled by user");
        assertThat(callback.amount()).isEmpty();
        assertThat(callback.receipt()).isEmpty();
    }

    @Test
    void theResultCodeCanBeAString() {
        StkCallback callback = StkCallback.parse("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_3","ResultCode":"2001","ResultDesc":"Wrong PIN"}}}""");

        assertThat(callback.resultCode()).isEqualTo(2001);
    }

    @Test
    void theWholeBodyCanArriveAsAJsonString() {
        String wrapped = "\"" + PAID.replace("\"", "\\\"") + "\"";

        assertThat(StkCallback.parse(wrapped).receipt()).contains("QK12ABC3DE");
    }

    @Test
    void anItemWithNoValueIsTreatedAsMissing() {
        StkCallback callback = StkCallback.parse("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_4","ResultCode":0,"ResultDesc":"ok",\
                "CallbackMetadata":{"Item":[{"Name":"Amount"},{"Name":"MpesaReceiptNumber","Value":"QK1"}]}}}}""");

        assertThat(callback.amount()).isEqualTo(Optional.empty());
        assertThat(callback.receipt()).contains("QK1");
    }

    @Test
    void anAmountThatIsntWholeShillingsIsNoAmount() {
        StkCallback callback = StkCallback.parse("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_5","ResultCode":0,"ResultDesc":"ok",\
                "CallbackMetadata":{"Item":[{"Name":"Amount","Value":2999.5}]}}}}""");

        assertThat(callback.amount()).isEmpty();
    }

    @Test
    void anythingElseIsNotACallback() {
        assertThatThrownBy(() -> StkCallback.parse("{\"hello\":\"world\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StkCallback.parse("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StkCallback.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StkCallback.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
