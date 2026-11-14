package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface InventoryDao {
    @Insert
    void insertItem(InventoryItem item);

    @Query("SELECT * FROM inventory_items")
    List<InventoryItem> getAllItems();
}
