package com.boxoffice.core.update;

import java.net.URI;

/** One file attached to a release: an installer, or the SHA256SUMS list. */
public record Asset(String name, URI url, long size) {
}
