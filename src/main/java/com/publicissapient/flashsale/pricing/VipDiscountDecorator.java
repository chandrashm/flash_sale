package com.publicissapient.flashsale.pricing;

import com.publicissapient.flashsale.model.PricingContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Decorator Pattern: Layers a 10% VIP loyalty discount on top of any underlying PricingStrategy
 * without modifying the core engine or existing strategies.
 */
public class VipDiscountDecorator implements PricingStrategy {
    private final PricingStrategy delegate;

    public VipDiscountDecorator(PricingStrategy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate PricingStrategy cannot be null");
    }

    @Override
    public BigDecimal calculatePrice(PricingContext context) {
        BigDecimal baseCalculated = delegate.calculatePrice(context);
        if (context.isVipUser()) {
            return baseCalculated
                    .multiply(BigDecimal.valueOf(0.90))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return baseCalculated;
    }
}
