package com.publicissapient.flashsale.pricing;

import com.publicissapient.flashsale.model.PricingContext;
import java.math.BigDecimal;

/**
 * Strategy interface for dynamic price calculation (Open/Closed & Dependency Inversion Principles).
 */
public interface PricingStrategy {
    BigDecimal calculatePrice(PricingContext context);
}
