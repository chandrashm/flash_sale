package com.publicissapient.flashsale.pricing;

import com.publicissapient.flashsale.model.PricingContext;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Increases price by 20% once remaining stock drops below 20% of initial inventory.
 */
public class TieredSurgePricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(PricingContext context) {
        if (context.getInitialStock() <= 0) {
            return context.getBasePrice();
        }
        double ratio = (double) context.getRemainingStock() / context.getInitialStock();
        if (ratio < 0.20) {
            return context.getBasePrice()
                    .multiply(BigDecimal.valueOf(1.20))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return context.getBasePrice().setScale(2, RoundingMode.HALF_UP);
    }
}
