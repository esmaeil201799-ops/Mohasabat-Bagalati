package com.esmaeil.groceryapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InventoryEngineTest {

    @Test
    public void calculatesWeightedAverageIncludingPerUnitTransport() {
        assertEquals(137L, InventoryEngine.calculateNewWeightedAverage(10, 100, 5, 200, 10));
    }

    @Test
    public void roundsHalfUpToSmallestCurrencyUnit() {
        assertEquals(2L, InventoryEngine.calculateNewWeightedAverage(1, 1, 1, 2, 0));
    }

    @Test
    public void avoidsIntermediateOverflow() {
        assertEquals(Long.MAX_VALUE, InventoryEngine.calculateNewWeightedAverage(
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeStockOrCost() {
        InventoryEngine.calculateNewWeightedAverage(2, 10, -1, 20, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroPurchaseQuantity() {
        InventoryEngine.calculateNewWeightedAverage(2, 10, 0, 20, 0);
    }
}
