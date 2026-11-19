package com.publicissapient.flashsale.model;

import java.math.BigDecimal;

/**
 * Immutable context passed to PricingStrategy implementations.
 * Enables extensible pricing rules (surge, VIP, happy hour) without changing method signatures.
 */
public final class PricingContext {
    private final BigDecimal basePrice;
    private final BigDecimal currentPrice;
    private final int remainingStock;
    private final int initialStock;
    private final boolean vipUser;

    public PricingContext(BigDecimal basePrice, BigDecimal currentPrice, int remainingStock, int initialStock, boolean vipUser) {
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
        this.remainingStock = remainingStock;
        this.initialStock = initialStock;
        this.vipUser = vipUser;
    }

    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public int getRemainingStock() { return remainingStock; }
    public int getInitialStock() { return initialStock; }
    public boolean isVipUser() { return vipUser; }
}
