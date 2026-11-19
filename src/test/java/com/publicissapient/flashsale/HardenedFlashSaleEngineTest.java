package com.publicissapient.flashsale;

import com.publicissapient.flashsale.engine.HardenedFlashSaleEngine;
import com.publicissapient.flashsale.model.ReservationResult;
import com.publicissapient.flashsale.pricing.TieredSurgePricingStrategy;
import com.publicissapient.flashsale.pricing.VipDiscountDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HardenedFlashSaleEngineTest {

    private HardenedFlashSaleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HardenedFlashSaleEngine(
                new VipDiscountDecorator(new TieredSurgePricingStrategy()),
                60_000
        );
        engine.registerSku("IPHONE-18", 50, BigDecimal.valueOf(1000.00));
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void testHappyPathSurgeAndVipDecorator() {
        // Deduct 41 units -> 9 left (<20% threshold), triggers surge for next buyer
        ReservationResult first = engine.blockStock("req-1", "IPHONE-18", 41, false);
        assertEquals(ReservationResult.Status.SUCCESS, first.getStatus());
        assertEquals(0, BigDecimal.valueOf(1000.00).compareTo(first.getLockedPrice()));

        // Second buyer gets surged price (1200.00), and updates next price for VIP
        ReservationResult second = engine.blockStock("req-2", "IPHONE-18", 1, true);
        assertEquals(ReservationResult.Status.SUCCESS, second.getStatus());
        assertEquals(0, BigDecimal.valueOf(1200.00).compareTo(second.getLockedPrice()));

        // Third buyer receives the VIP discounted surged price (1200 * 0.90 = 1080.00)
        ReservationResult third = engine.blockStock("req-3", "IPHONE-18", 1, false);
        assertEquals(ReservationResult.Status.SUCCESS, third.getStatus());
        assertEquals(0, BigDecimal.valueOf(1080.00).compareTo(third.getLockedPrice()));
    }

    @Test
    void testIdempotencyPreventsDuplicateDeductions() {
        ReservationResult first = engine.blockStock("req-dup", "IPHONE-18", 5, false);
        ReservationResult retry = engine.blockStock("req-dup", "IPHONE-18", 5, false);

        assertEquals(ReservationResult.Status.SUCCESS, first.getStatus());
        assertEquals(ReservationResult.Status.SUCCESS, retry.getStatus());
        assertEquals(45, engine.getAvailableStock("IPHONE-18"));
    }

    @Test
    void testInvalidInputsFailGracefully() {
        assertEquals(ReservationResult.Status.INVALID_REQUEST, engine.blockStock(null, "IPHONE-18", 1).getStatus());
        assertEquals(ReservationResult.Status.INVALID_REQUEST, engine.blockStock("req-a", " ", 1).getStatus());
        assertEquals(ReservationResult.Status.INVALID_REQUEST, engine.blockStock("req-b", "IPHONE-18", 0).getStatus());
        assertEquals(ReservationResult.Status.SKU_NOT_FOUND, engine.blockStock("req-c", "NO-SUCH-SKU", 1).getStatus());
    }

    @Test
    void testHighConcurrencyNoOverselling() throws InterruptedException {
        int totalThreads = 2000;
        int initialStock = 50;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final String reqId = "concurrent-req-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    ReservationResult result = engine.blockStock(reqId, "IPHONE-18", 1, false);
                    if (result.getStatus() == ReservationResult.Status.SUCCESS) {
                        successCount.incrementAndGet();
                    } else if (result.getStatus() == ReservationResult.Status.OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(initialStock, successCount.get());
        assertEquals(totalThreads - initialStock, outOfStockCount.get());
        assertEquals(0, engine.getAvailableStock("IPHONE-18"));
    }
}
