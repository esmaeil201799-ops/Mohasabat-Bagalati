package com.esmaeil.groceryapp;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {
        Account.class,
        JournalEntry.class,
        JournalLine.class,
        InventoryItem.class,
        Unit.class,
        StockMovement.class
}, version = 4, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AccountDao accountDao();
    public abstract InventoryDao inventoryDao();

    // الترحيل الآمن والصريح من الإصدار 3 إلى الإصدار 4
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS units (unitId TEXT PRIMARY KEY NOT NULL, unitName TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (movementId TEXT PRIMARY KEY NOT NULL, itemId TEXT, referenceId TEXT, movementType TEXT, quantityChange INTEGER NOT NULL, costAtTheTime INTEGER NOT NULL, date TEXT)");
        }
    };
}
