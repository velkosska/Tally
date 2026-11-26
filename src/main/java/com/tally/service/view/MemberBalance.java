package com.tally.service.view;

import java.math.BigDecimal;

/**
 * How one member stands: what they paid, their share of the costs, and the difference.
 * {@code barPercent} is the size of their balance bar relative to the largest balance.
 */
public record MemberBalance(long memberId, String name, BigDecimal paid, BigDecimal share,
                            BigDecimal net, int barPercent) {

    public boolean isOwed() {
        return net.signum() > 0;
    }

    public boolean isOwing() {
        return net.signum() < 0;
    }
}
