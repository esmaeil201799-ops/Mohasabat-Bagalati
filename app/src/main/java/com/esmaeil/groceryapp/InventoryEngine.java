package com.esmaeil.groceryapp;

public class InventoryEngine {

    /**
     * يحسب المتوسط المرجح المتحرك الجديد للتكلفة مع دمج مصاريف النقل بدقة تامة (باستخدام Long فقط)
     */
    public static long calculateNewWeightedAverage(long currentQuantity, long currentAverageCost, 
                                                   long purchasedQuantity, long actualPurchasePrice, 
                                                   long transportShare) {
        if (currentQuantity + purchasedQuantity <= 0) {
            return actualPurchasePrice + transportShare;
        }

        long totalOldValue = currentQuantity * currentAverageCost;
        long totalNewValue = purchasedQuantity * (actualPurchasePrice + transportShare);
        
        return (totalOldValue + totalNewValue) / (currentQuantity + purchasedQuantity);
    }
}
