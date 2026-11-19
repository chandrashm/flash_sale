package com.publicissapient.flashsale.engine;

import com.publicissapient.flashsale.model.PricingContext;
import com.publicissapient.flashsale.model.ReservationResult;
import com.publicissapient.flashsale.model.SkuSnapshot;
import com.publicissapient.flashsale.pricing.PricingStrategy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free Flash Sale Inventory & Dynamic Pricing Engine using Compare-And-Swap (CAS).
 * Zero synchronized blocks or ReentrantLocks are used.
 */
public class HardenedFlashSaleEngine implements AutoCloseable {

    private static final class CachedResponse {
        final ReservationResult result;
        final long expiresAtMillis;
        final boolean inProgress;

        private CachedResponse(ReservationResult result, long expiresAtMillis, boolean inProgress) {
            this.result = result;
            this.expiresAtMillis = expiresAtMillis;
            this.inProgress = inProgress;
        }

        static CachedResponse inProgress(long ttlMillis) {
            return new CachedResponse(null, System.currentTimeMillis() + ttlMillis, true);
        }

        static CachedResponse completed(ReservationResult result, long ttlMillis) {
            return new CachedResponse(result, System.currentTimeMillis() + ttlMillis, false);
        }
    }

    private final ConcurrentHashMap<String, AtomicReference<SkuSnapshot>> catalog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final PricingStrategy pricingStrategy;
    private final long cacheTtlMillis;
    private final ScheduledExecutorService cleanupScheduler;

    public HardenedFlashSaleEngine(PricingStrategy pricingStrategy, long cacheTtlMillis) {
        this.pricingStrategy = Objects.requireNonNull(pricingStrategy, "PricingStrategy cannot be null");
        if (cacheTtlMillis <= 0) {
            throw new IllegalArgumentException("cacheTtlMillis must be > 0");
        }
        this.cacheTtlMillis = cacheTtlMillis;

        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idempotency-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(
                this::evictExpiredEntries, cacheTtlMillis, cacheTtlMillis, TimeUnit.MILLISECONDS
        );
    }

    public void registerSku(String skuId, int initialStock, BigDecimal basePrice) {
        if (isBlank(skuId) || initialStock < 0 || basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid SKU registration parameters");
        }
        catalog.put(skuId, new AtomicReference<>(new SkuSnapshot(initialStock, initialStock, basePrice, basePrice)));
    }

    public ReservationResult blockStock(String requestId, String skuId, int quantity) {
        return blockStock(requestId, skuId, quantity, false);
    }

    public ReservationResult blockStock(String requestId, String skuId, int quantity, boolean isVipUser) {
        if (isBlank(requestId) || isBlank(skuId) || quantity <= 0) {
            return ReservationResult.failure(ReservationResult.Status.INVALID_REQUEST, "Invalid input parameters");
        }

        // 1. Atomic Duplicate-Request Guard
        CachedResponse inFlight = CachedResponse.inProgress(cacheTtlMillis);
        CachedResponse existing = idempotencyCache.putIfAbsent(requestId, inFlight);

        if (existing != null) {
            if (System.currentTimeMillis() > existing.expiresAtMillis) {
                idempotencyCache.remove(requestId, existing);
                return blockStock(requestId, skuId, quantity, isVipUser);
            }
            if (existing.inProgress) {
                return ReservationResult.failure(ReservationResult.Status.INVALID_REQUEST, "Duplicate request currently in flight");
            }
            return existing.result;
        }

        // 2. Lock-Free CAS Stock & Price Transition
        try {
            ReservationResult outcome = executeCasDeduction(skuId, quantity, isVipUser);
            idempotencyCache.replace(requestId, inFlight, CachedResponse.completed(outcome, cacheTtlMillis));
            return outcome;
        } catch (RuntimeException ex) {
            idempotencyCache.remove(requestId, inFlight);
            throw ex;
        }
    }

    private ReservationResult executeCasDeduction(String skuId, int quantity, boolean isVipUser) {
        AtomicReference<SkuSnapshot> stateRef = catalog.get(skuId);
        if (stateRef == null) {
            return ReservationResult.failure(ReservationResult.Status.SKU_NOT_FOUND, "SKU not found");
        }

        while (true) {
            SkuSnapshot current = stateRef.get();
            if (current.getAvailableStock() < quantity) {
                return ReservationResult.failure(ReservationResult.Status.OUT_OF_STOCK, "Insufficient stock");
            }

            int updatedStock = current.getAvailableStock() - quantity;
            PricingContext context = new PricingContext(
                    current.getBasePrice(),
                    current.getCurrentPrice(),
                    updatedStock,
                    current.getInitialStock(),
                    isVipUser
            );

            BigDecimal nextPrice = pricingStrategy.calculatePrice(context);
            SkuSnapshot nextSnapshot = new SkuSnapshot(
                    current.getInitialStock(),
                    updatedStock,
                    current.getBasePrice(),
                    nextPrice
            );

            if (stateRef.compareAndSet(current, nextSnapshot)) {
                return ReservationResult.success(current.getCurrentPrice());
            }
            Thread.onSpinWait();
        }
    }

    public int getAvailableStock(String skuId) {
        AtomicReference<SkuSnapshot> ref = catalog.get(skuId);
        return (ref == null) ? 0 : ref.get().getAvailableStock();
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        idempotencyCache.entrySet().removeIf(entry -> now > entry.getValue().expiresAtMillis);
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    @Override
    public void close() {
        cleanupScheduler.shutdownNow();
    }
}
