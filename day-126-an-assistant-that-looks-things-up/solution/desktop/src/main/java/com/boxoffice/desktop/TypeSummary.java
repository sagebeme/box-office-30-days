package com.boxoffice.desktop;

/** One ticket type on the dashboard: "VIP · KES 5,000 · 49 of 50 left". */
public record TypeSummary(String name, String price, int left, int capacity) {

    @Override
    public String toString() {
        return name + " · " + price + " · " + left + " of " + capacity + " left";
    }
}
