package com.publicissapient.flashsale;

import com.publicissapient.flashsale.engine.HardenedFlashSaleEngine;
import com.publicissapient.flashsale.model.ReservationResult;
import com.publicissapient.flashsale.pricing.PricingStrategy;
import com.publicissapient.flashsale.pricing.TieredSurgePricingStrategy;
import com.publicissapient.flashsale.pricing.VipDiscountDecorator;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interactive console demo showcasing:
 * 1. Strategy + Decorator Pattern (Tiered Surge + VIP Discount)
 * 2. Idempotency Guard
 * 3. High-Concurrency Lock-Free Stress Simulation (1,000 concurrent threads)
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        PricingStrategy strategy = new VipDiscountDecorator(new TieredSurgePricingStrategy());

        try (HardenedFlashSaleEngine engine = new HardenedFlashSaleEngine(strategy, 60_000)) {
            String sku = "IPHONE-18-PRO";
            int initialStock = 25;
            engine.registerSku(sku, initialStock, BigDecimal.valueOf(1000.00));

            System.out.println("=== 1. Single Request & Idempotency Demo ===");
            ReservationResult r1 = engine.blockStock("order-101", sku, 5, false);
            ReservationResult r1Dup = engine.blockStock("order-101", sku, 5, false);
            System.out.println("First attempt : " + r1);
            System.out.println("Retry attempt : " + r1Dup + " (Remaining stock: " + engine.getAvailableStock(sku) + ")");

            System.out.println("\n=== 2. 1,000-Thread Concurrent Flash Sale Simulation ===");
            int totalThreads = 1000;
            ExecutorService pool = Executors.newFixedThreadPool(100);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(totalThreads);
            AtomicInteger wins = new AtomicInteger();
            AtomicInteger outOfStock = new AtomicInteger();

            for (int i = 0; i < totalThreads; i++) {
                final String reqId = "user-req-" + i;
                final boolean isVip = (i % 5 == 0);
                pool.submit(() -> {
                    try {
                        startGate.await();
                        ReservationResult res = engine.blockStock(reqId, sku, 1, isVip);
                        if (res.getStatus() == ReservationResult.Status.SUCCESS) {
                            wins.incrementAndGet();
                        } else if (res.getStatus() == ReservationResult.Status.OUT_OF_STOCK) {
                            outOfStock.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            doneGate.await();
            pool.shutdown();

            System.out.println("Initial Stock      : " + initialStock);
            System.out.println("Successful Orders  : " + (wins.get() + 1) + " (5 units initial + " + wins.get() + " concurrent units)");
            System.out.println("Out-of-Stock Count : " + outOfStock.get());
            System.out.println("Final Stock        : " + engine.getAvailableStock(sku));
        }
    }
}
