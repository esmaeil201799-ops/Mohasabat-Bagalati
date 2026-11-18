package com.esmaeil.groceryapp;

import java.math.BigInteger;

public class InventoryEngine {

    /**
     * يحسب متوسط التكلفة المرجح الجديد. transportShare هي حصة النقل لكل وحدة مشتراة،
     * وتُقرّب النتيجة إلى أقرب وحدة نقدية صغرى (والنصف إلى الأعلى).
     */
    public static long calculateNewWeightedAverage(long currentQuantity, long currentAverageCost, 
                                                   long purchasedQuantity, long actualPurchasePrice, 
                                                   long transportShare) {
        if (currentQuantity < 0 || currentAverageCost < 0 || purchasedQuantity <= 0
                || actualPurchasePrice < 0 || transportShare < 0) {
            throw new IllegalArgumentException("الكميات والتكاليف يجب أن تكون غير سالبة، وكمية الشراء موجبة");
        }

        BigInteger oldValue = BigInteger.valueOf(currentQuantity)
                .multiply(BigInteger.valueOf(currentAverageCost));
        BigInteger landedUnitCost = BigInteger.valueOf(actualPurchasePrice)
                .add(BigInteger.valueOf(transportShare));
        BigInteger newValue = BigInteger.valueOf(purchasedQuantity).multiply(landedUnitCost);
        BigInteger totalQuantity = BigInteger.valueOf(currentQuantity)
                .add(BigInteger.valueOf(purchasedQuantity));
        BigInteger[] quotientAndRemainder = oldValue.add(newValue).divideAndRemainder(totalQuantity);

        if (quotientAndRemainder[1].shiftLeft(1).compareTo(totalQuantity) >= 0) {
            quotientAndRemainder[0] = quotientAndRemainder[0].add(BigInteger.ONE);
        }
        return quotientAndRemainder[0].longValueExact();
    }
}
