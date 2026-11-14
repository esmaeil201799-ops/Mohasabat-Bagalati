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
        UnitConversion.class,
        CashBox.class,
        Currency.class,
        ExchangeRate.class,
        CustomerSupplier.class,
        PurchaseInvoice.class,
        PurchaseItem.class,
        SalesInvoice.class,
        SalesItem.class,
        Return.class,
        ReturnItem.class,
        ReceiptVoucher.class,
        PaymentVoucher.class,
        InvoiceAllocation.class,
        StockMovement.class,
        Expense.class,
        Drawing.class,
        Damage.class,
        SystemSettings.class,
        User.class,
        AuditLog.class
}, version = 4, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AccountDao accountDao();
    public abstract InventoryDao inventoryDao();
    public abstract InvoiceDao invoiceDao();
    public abstract CustomerSupplierDao customerSupplierDao();
    public abstract ExpenseDao expenseDao();

    // الترحيل الآمن والصريح من الإصدار 3 إلى الإصدار 4 (يحفظ رصيد الصندوق والأصناف القديمة تماماً)
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS units (unitId TEXT PRIMARY KEY NOT NULL, unitName TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (movementId TEXT PRIMARY KEY NOT NULL, itemId TEXT, referenceId TEXT, movementType TEXT, quantityChange INTEGER NOT NULL, costAtTheTime INTEGER NOT NULL, date TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS invoice_allocations (allocationId TEXT PRIMARY KEY NOT NULL, voucherId TEXT, invoiceId TEXT, allocatedAmount INTEGER NOT NULL, date TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS cash_boxes (boxId TEXT PRIMARY KEY NOT NULL, boxName TEXT, currencyCode TEXT, balance INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS currencies (currencyCode TEXT PRIMARY KEY NOT NULL, currencyName TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS exchange_rates (rateId TEXT PRIMARY KEY NOT NULL, fromCurrency TEXT, toCurrency TEXT, rateValue INTEGER NOT NULL, effectiveDate TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS returns (returnId TEXT PRIMARY KEY NOT NULL, originalInvoiceId TEXT, returnType TEXT, date TEXT, totalAmount INTEGER NOT NULL, isPosted INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS return_items (id TEXT PRIMARY KEY NOT NULL, returnId TEXT, itemId TEXT, quantity INTEGER NOT NULL, unitId TEXT, price INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS audit_logs (logId TEXT PRIMARY KEY NOT NULL, userId TEXT, action TEXT, timestamp TEXT)");
        }
    };
}
