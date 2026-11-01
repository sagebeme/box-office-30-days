package com.boxoffice.core.sync;

import java.util.List;

/**
 * A sale the server refused because the places had gone: for staff to sort
 * out with the buyer (a refund, or another ticket type).
 *
 * @param detail what the server said, like "Only 0 VIP places were left"
 */
public record SyncConflict(List<String> codes, String buyer, String phone, String detail) {

    /** "A-VIP-0050 (Wanjiku, 254712345678): Only 0 VIP places were left" */
    public String describe() {
        String who = phone.isEmpty() ? buyer : buyer + ", " + phone;
        return String.join(", ", codes) + " (" + who + "): " + detail;
    }
}
