package com.delfino.expensetracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for normalizing monetary/quantity values before persistence so that
 * they satisfy the {@code @Digits} constraints on {@code ExpenseItem}
 * (quantity: 4 integer / 4 fraction digits, unitPrice: 10/4, adjustment: 10/5).
 * <p>
 * Values coming from OCR/LLM parsing or arbitrary API/tool input can carry
 * more fractional digits than the schema allows (e.g. a unit price derived
 * from {@code totalPrice / quantity} such as {@code 2.54 / 3 = 0.8466666...}).
 * Naively rounding {@code unitPrice} alone would silently change the line's
 * total ({@code quantity * unitPrice}), so any rounding error introduced by
 * truncating {@code unitPrice} is absorbed into {@code adjustment} — this
 * keeps {@code quantity * unitPrice + adjustment} exactly equal to the
 * original observed total (up to 1e-5), while every stored field stays
 * within its allowed precision.
 */
public final class MoneyUtils {

    private static final int QUANTITY_SCALE = 4;
    private static final int UNIT_PRICE_SCALE = 4;
    private static final int ADJUSTMENT_SCALE = 5;

    private MoneyUtils() {}

    /**
     * Result of normalizing a line item's pricing fields.
     */
    public record LineItemPricing(BigDecimal quantity, BigDecimal unitPrice, BigDecimal adjustment) {}

    /**
     * Rounds {@code quantity} and {@code unitPrice} to the precision allowed by
     * {@code ExpenseItem}, and folds whatever rounding error that introduces
     * into {@code adjustment} so that the line's total
     * ({@code quantity * unitPrice + adjustment}) is preserved.
     *
     * @param quantity   raw quantity (defaults to {@link BigDecimal#ONE} if null)
     * @param unitPrice  raw unit price (defaults to {@link BigDecimal#ZERO} if null)
     * @param adjustment raw adjustment (defaults to {@link BigDecimal#ZERO} if null)
     */
    public static LineItemPricing normalizeLineItemPricing(BigDecimal quantity, BigDecimal unitPrice, BigDecimal adjustment) {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE;
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal adj = adjustment != null ? adjustment : BigDecimal.ZERO;

        // The exact total as originally observed/computed, before any rounding.
        BigDecimal originalTotal = qty.multiply(price).add(adj);

        BigDecimal roundedQty = qty.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal roundedPrice = price.setScale(UNIT_PRICE_SCALE, RoundingMode.HALF_UP);

        // Whatever total we'd get after rounding quantity/unitPrice alone.
        BigDecimal roundedSubtotal = roundedQty.multiply(roundedPrice);

        // Absorb the rounding delta into adjustment so quantity*unitPrice+adjustment
        // still equals the original total (within adjustment's own scale).
        BigDecimal newAdjustment = originalTotal.subtract(roundedSubtotal).setScale(ADJUSTMENT_SCALE, RoundingMode.HALF_UP);

        return new LineItemPricing(roundedQty, roundedPrice, newAdjustment);
    }
}

