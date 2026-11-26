package com.tally.service.view;

import java.math.BigDecimal;

/**
 * How one member stands: what they paid, their share of the costs, what they already paid back
 * (negative when others paid them back), and the resulting balance.
 * {@code barPercent} is the size of their balance bar relative to the largest balance.
 */
public record MemberBalance(long memberId, String name, BigDecimal paid, BigDecimal share,
                            BigDecimal repaid, BigDecimal net, int barPercent) {

    public boolean isOwed() {
        return net.signum() > 0;
    }

    public boolean isOwing() {
        return net.signum() < 0;
    }
}
