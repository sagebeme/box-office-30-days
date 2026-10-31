package com.boxoffice.server.mpesa;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Practice M-Pesa: says yes to every prompt and sends nothing to any phone.
 * The order page shows "Pretend to pay" buttons instead, which send the
 * callback Safaricom would.
 */
public class StubDaraja implements Daraja {

    private final List<StkRequest> requests = new CopyOnWriteArrayList<>();

    @Override
    public StkAnswer stkPush(StkRequest request) {
        requests.add(request);
        return new StkAnswer("ws_CO_STUB_" + UUID.randomUUID(), "stub-" + requests.size());
    }

    /** Every prompt asked for so far, for the tests. */
    public List<StkRequest> requests() {
        return List.copyOf(requests);
    }
}
