package com.boxoffice.core.lan;

import com.boxoffice.core.sync.OutboxEntry;
import java.util.List;

/**
 * One page of a gate's feed, as JSON:
 *
 * <pre>
 * {"gate":"A","sales":[{"id":7,"key":"6f1c...","payload":"{\"eventId\":...}","attempts":0}]}
 * </pre>
 */
public record FeedPage(String gate, List<OutboxEntry> sales) {
}
