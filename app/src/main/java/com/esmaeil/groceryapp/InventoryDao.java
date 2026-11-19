package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertItem(InventoryItem item);

    @Query("SELECT * FROM inventory_items WHERE itemId = :itemId LIMIT 1")
    InventoryItem getItem(String itemId);

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    InventoryItem getItemByBarcode(String barcode);

    @Query("SELECT * FROM stock_movements WHERE movementId = :movementId LIMIT 1")
    StockMovement getMovementById(String movementId);

    @Query("SELECT * FROM stock_movements WHERE referenceId = :referenceId "
            + "AND movementType = :movementType LIMIT 1")
    StockMovement getMovementByReference(String referenceId, String movementType);

    @Insert
    void insertMovement(StockMovement movement);

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY itemName COLLATE NOCASE")
    List<InventoryItem> getAllItems();

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 AND "
            + "(itemName LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') "
            + "ORDER BY itemName COLLATE NOCASE")
    List<InventoryItem> searchItems(String query);

    @Update
    int updateItem(InventoryItem item);

    @Query("UPDATE inventory_items SET isActive = 0 WHERE itemId = :itemId AND isActive = 1")
    int softDeleteItem(String itemId);

    @Query("UPDATE inventory_items SET quantity = :quantity, "
            + "purchasePriceAverage = :averageCost WHERE itemId = :itemId AND isActive = 1")
    int updateStockAndAverageCost(String itemId, long quantity, long averageCost);

    @Query("UPDATE inventory_items SET quantity = quantity - :quantity "
            + "WHERE itemId = :itemId AND isActive = 1 AND quantity >= :quantity")
    int decreaseStock(String itemId, long quantity);

    @Query("SELECT COUNT(*) FROM inventory_items WHERE isActive = 1")
    int countActiveItems();
}
