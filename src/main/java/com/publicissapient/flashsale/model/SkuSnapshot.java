package com.publicissapient.flashsale.model;

import java.math.BigDecimal;

/**
 * Immutable value object representing the point-in-time state of a SKU.
 * Final fields guarantee safe publication under the Java Memory Model (JMM)
 * and eliminate the ABA problem during CAS transitions.
 */
public final class SkuSnapshot {
    private final int initialStock;
    private final int availableStock;
    private final BigDecimal basePrice;
    private final BigDecimal currentPrice;

    public SkuSnapshot(int initialStock, int availableStock, BigDecimal basePrice, BigDecimal currentPrice) {
        this.initialStock = initialStock;
        this.availableStock = availableStock;
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
    }

    public int getInitialStock() { return initialStock; }
    public int getAvailableStock() { return availableStock; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
}
