package com.publicissapient.flashsale.model;

import java.math.BigDecimal;

/**
 * Lightweight outcome object used instead of throwing exceptions for expected business rejections
 * (such as OUT_OF_STOCK), avoiding expensive fillInStackTrace() overhead under flash sale load.
 */
public final class ReservationResult {
    public enum Status { SUCCESS, OUT_OF_STOCK, SKU_NOT_FOUND, INVALID_REQUEST }

    private final Status status;
    private final BigDecimal lockedPrice;
    private final String message;

    private ReservationResult(Status status, BigDecimal lockedPrice, String message) {
        this.status = status;
        this.lockedPrice = lockedPrice;
        this.message = message;
    }

    public static ReservationResult success(BigDecimal price) {
        return new ReservationResult(Status.SUCCESS, price, "Stock reserved");
    }

    public static ReservationResult failure(Status status, String message) {
        return new ReservationResult(status, BigDecimal.ZERO, message);
    }

    public Status getStatus() { return status; }
    public BigDecimal getLockedPrice() { return lockedPrice; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "ReservationResult{status=" + status + ", lockedPrice=" + lockedPrice + ", message='" + message + "'}";
    }
}
