package com.esmaeil.groceryapp;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {
        Account.class,
        JournalEntry.class,
        JournalLine.class,
        InventoryItem.class,
        Unit.class,
        StockMovement.class,
        UnitConversion.class,
        BusinessPartner.class,
        Invoice.class,
        InvoiceLine.class,
        Payment.class,
        CashBox.class,
        CashMovement.class,
        Expense.class,
        OwnerWithdrawal.class,
        AppUser.class,
        AuditEvent.class,
        AppSetting.class,
        WorkShift.class,
        LegacyRecord.class
}, version = 9, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DATABASE_NAME = "grocery_erp_db";
    public static final int SCHEMA_VERSION = 9;

    public abstract AccountDao accountDao();
    public abstract InventoryDao inventoryDao();
    public abstract ErpDao erpDao();

    public static AppDatabase open(Context context) {
        return open(context, DATABASE_NAME);
    }

    static AppDatabase open(Context context, String databaseName) {
        return Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, databaseName)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(SupportSQLiteDatabase database) {
                        installJournalIntegrityTriggers(database);
                    }
                })
                .build();
    }

    // الترحيل الآمن والصريح من الإصدار 3 إلى الإصدار 4
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS units (unitId TEXT PRIMARY KEY NOT NULL, unitName TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (movementId TEXT PRIMARY KEY NOT NULL, itemId TEXT, referenceId TEXT, movementType TEXT, quantityChange INTEGER NOT NULL, costAtTheTime INTEGER NOT NULL, date TEXT)");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS business_partners ("
                    + "partnerId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, "
                    + "phone TEXT, address TEXT, createdAt INTEGER NOT NULL, isActive INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_business_partners_type "
                    + "ON business_partners(type)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_business_partners_name "
                    + "ON business_partners(name)");

            database.execSQL("CREATE TABLE IF NOT EXISTS cash_boxes ("
                    + "cashBoxId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, "
                    + "isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_boxes_name ON cash_boxes(name)");

            database.execSQL("CREATE TABLE IF NOT EXISTS app_users ("
                    + "userId TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, role TEXT NOT NULL, "
                    + "pinSalt BLOB, pinVerifier BLOB, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_users_username "
                    + "ON app_users(username)");

            database.execSQL("CREATE TABLE IF NOT EXISTS app_settings ("
                    + "settingKey TEXT NOT NULL PRIMARY KEY, settingValue TEXT NOT NULL)");

            database.execSQL("CREATE TABLE IF NOT EXISTS invoices ("
                    + "invoiceId TEXT NOT NULL PRIMARY KEY, idempotencyKey TEXT NOT NULL, "
                    + "requestFingerprint TEXT NOT NULL, type TEXT NOT NULL, partnerId TEXT, "
                    + "cashBoxId TEXT, journalEntryId TEXT, issuedAt INTEGER NOT NULL, "
                    + "subtotal INTEGER NOT NULL, discount INTEGER NOT NULL, total INTEGER NOT NULL, "
                    + "paid INTEGER NOT NULL, status TEXT NOT NULL, "
                    + "FOREIGN KEY(partnerId) REFERENCES business_partners(partnerId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(cashBoxId) REFERENCES cash_boxes(cashBoxId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(journalEntryId) REFERENCES journal_entries(entryId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_idempotencyKey "
                    + "ON invoices(idempotencyKey)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_partnerId ON invoices(partnerId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_cashBoxId ON invoices(cashBoxId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_journalEntryId "
                    + "ON invoices(journalEntryId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_issuedAt ON invoices(issuedAt)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_type ON invoices(type)");

            database.execSQL("CREATE TABLE IF NOT EXISTS invoice_lines ("
                    + "lineId TEXT NOT NULL PRIMARY KEY, invoiceId TEXT NOT NULL, itemId TEXT NOT NULL, "
                    + "quantity INTEGER NOT NULL, unitPrice INTEGER NOT NULL, "
                    + "transportPerUnit INTEGER NOT NULL, costAtPosting INTEGER NOT NULL, "
                    + "lineTotal INTEGER NOT NULL, "
                    + "FOREIGN KEY(invoiceId) REFERENCES invoices(invoiceId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(itemId) REFERENCES inventory_items(itemId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_invoiceId "
                    + "ON invoice_lines(invoiceId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_itemId "
                    + "ON invoice_lines(itemId)");

            database.execSQL("CREATE TABLE IF NOT EXISTS payments ("
                    + "paymentId TEXT NOT NULL PRIMARY KEY, idempotencyKey TEXT NOT NULL, "
                    + "direction TEXT NOT NULL, invoiceId TEXT, partnerId TEXT, cashBoxId TEXT, "
                    + "journalEntryId TEXT, amount INTEGER NOT NULL, discount INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(invoiceId) REFERENCES invoices(invoiceId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(partnerId) REFERENCES business_partners(partnerId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(cashBoxId) REFERENCES cash_boxes(cashBoxId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(journalEntryId) REFERENCES journal_entries(entryId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payments_idempotencyKey "
                    + "ON payments(idempotencyKey)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_payments_invoiceId ON payments(invoiceId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_payments_partnerId ON payments(partnerId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_payments_cashBoxId ON payments(cashBoxId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_payments_journalEntryId "
                    + "ON payments(journalEntryId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_payments_createdAt ON payments(createdAt)");

            database.execSQL("CREATE TABLE IF NOT EXISTS cash_movements ("
                    + "movementId TEXT NOT NULL PRIMARY KEY, cashBoxId TEXT NOT NULL, "
                    + "referenceType TEXT NOT NULL, referenceId TEXT NOT NULL, amount INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(cashBoxId) REFERENCES cash_boxes(cashBoxId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_cashBoxId "
                    + "ON cash_movements(cashBoxId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_referenceType "
                    + "ON cash_movements(referenceType)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_referenceId "
                    + "ON cash_movements(referenceId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_createdAt "
                    + "ON cash_movements(createdAt)");

            database.execSQL("CREATE TABLE IF NOT EXISTS expenses ("
                    + "expenseId TEXT NOT NULL PRIMARY KEY, idempotencyKey TEXT NOT NULL, "
                    + "description TEXT NOT NULL, cashBoxId TEXT NOT NULL, journalEntryId TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(cashBoxId) REFERENCES cash_boxes(cashBoxId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(journalEntryId) REFERENCES journal_entries(entryId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expenses_idempotencyKey "
                    + "ON expenses(idempotencyKey)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_cashBoxId ON expenses(cashBoxId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_journalEntryId "
                    + "ON expenses(journalEntryId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_createdAt ON expenses(createdAt)");

            database.execSQL("CREATE TABLE IF NOT EXISTS audit_events ("
                    + "eventId TEXT NOT NULL PRIMARY KEY, userId TEXT, action TEXT NOT NULL, "
                    + "entityType TEXT NOT NULL, entityId TEXT NOT NULL, details TEXT NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(userId) REFERENCES app_users(userId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_userId ON audit_events(userId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_createdAt "
                    + "ON audit_events(createdAt)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_entityType "
                    + "ON audit_events(entityType)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_entityId "
                    + "ON audit_events(entityId)");

            database.execSQL("CREATE TABLE IF NOT EXISTS work_shifts ("
                    + "shiftId TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, cashBoxId TEXT NOT NULL, "
                    + "openedAt INTEGER NOT NULL, closedAt INTEGER, openingCash INTEGER NOT NULL, "
                    + "expectedCash INTEGER, actualCash INTEGER, status TEXT NOT NULL, "
                    + "FOREIGN KEY(userId) REFERENCES app_users(userId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(cashBoxId) REFERENCES cash_boxes(cashBoxId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_work_shifts_userId ON work_shifts(userId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_work_shifts_cashBoxId "
                    + "ON work_shifts(cashBoxId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_work_shifts_openedAt "
                    + "ON work_shifts(openedAt)");

            database.execSQL("CREATE TABLE IF NOT EXISTS unit_conversions ("
                    + "conversionId TEXT NOT NULL PRIMARY KEY, itemId TEXT NOT NULL, unitId TEXT NOT NULL, "
                    + "baseUnitsPerUnit INTEGER NOT NULL, "
                    + "FOREIGN KEY(itemId) REFERENCES inventory_items(itemId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(unitId) REFERENCES units(unitId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_unit_conversions_itemId_unitId "
                    + "ON unit_conversions(itemId, unitId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_unit_conversions_unitId "
                    + "ON unit_conversions(unitId)");

            database.execSQL("CREATE TABLE journal_lines_new ("
                    + "lineId TEXT NOT NULL PRIMARY KEY, entryId TEXT NOT NULL, accountId TEXT NOT NULL, "
                    + "partnerId TEXT, debit INTEGER NOT NULL, credit INTEGER NOT NULL, "
                    + "FOREIGN KEY(entryId) REFERENCES journal_entries(entryId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(accountId) REFERENCES accounts(accountId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(partnerId) REFERENCES business_partners(partnerId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("INSERT INTO journal_lines_new "
                    + "(lineId, entryId, accountId, partnerId, debit, credit) "
                    + "SELECT lineId, entryId, accountId, NULL, debit, credit FROM journal_lines");
            database.execSQL("DROP TABLE journal_lines");
            database.execSQL("ALTER TABLE journal_lines_new RENAME TO journal_lines");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_lines_entryId "
                    + "ON journal_lines(entryId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_lines_accountId "
                    + "ON journal_lines(accountId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_lines_partnerId "
                    + "ON journal_lines(partnerId)");

            database.execSQL("CREATE TABLE stock_movements_new ("
                    + "movementId TEXT NOT NULL PRIMARY KEY, itemId TEXT, referenceId TEXT, "
                    + "movementType TEXT, quantityChange INTEGER NOT NULL, costAtTheTime INTEGER NOT NULL, "
                    + "date TEXT, "
                    + "FOREIGN KEY(itemId) REFERENCES inventory_items(itemId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("INSERT INTO stock_movements_new "
                    + "(movementId, itemId, referenceId, movementType, quantityChange, costAtTheTime, date) "
                    + "SELECT movementId, itemId, referenceId, movementType, quantityChange, costAtTheTime, date "
                    + "FROM stock_movements");
            database.execSQL("DROP TABLE stock_movements");
            database.execSQL("ALTER TABLE stock_movements_new RENAME TO stock_movements");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_itemId "
                    + "ON stock_movements(itemId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_referenceId "
                    + "ON stock_movements(referenceId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_date "
                    + "ON stock_movements(date)");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("UPDATE inventory_items SET barcode = NULL "
                    + "WHERE barcode IS NOT NULL AND TRIM(barcode) = ''");

            try (android.database.Cursor duplicate = database.query(
                    "SELECT barcode FROM inventory_items WHERE barcode IS NOT NULL "
                            + "GROUP BY barcode HAVING COUNT(*) > 1 LIMIT 1")) {
                if (duplicate.moveToFirst()) {
                    throw new IllegalStateException(
                            "تعذر ترقية قاعدة البيانات: يوجد باركود مكرر. "
                                    + "احتفظ بنسخة احتياطية وصحح التكرار قبل إعادة المحاولة.");
                }
            }

            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_items_barcode "
                    + "ON inventory_items(barcode)");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE inventory_items ADD COLUMN isActive "
                    + "INTEGER NOT NULL DEFAULT 1");
            database.execSQL("CREATE TABLE IF NOT EXISTS legacy_archive ("
                    + "dataset TEXT NOT NULL, recordKey TEXT NOT NULL, payload TEXT NOT NULL, "
                    + "importedAt INTEGER NOT NULL, PRIMARY KEY(dataset, recordKey))");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE app_users ADD COLUMN recoverySalt BLOB");
            database.execSQL("ALTER TABLE app_users ADD COLUMN recoveryVerifier BLOB");
            database.execSQL("ALTER TABLE app_users ADD COLUMN failedPinAttempts "
                    + "INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE app_users ADD COLUMN pinLockedUntil "
                    + "INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE invoices ADD COLUMN originalInvoiceId TEXT "
                    + "REFERENCES invoices(invoiceId) ON UPDATE NO ACTION ON DELETE RESTRICT");
            database.execSQL("ALTER TABLE invoices ADD COLUMN currencyCode TEXT NOT NULL "
                    + "DEFAULT 'YER'");
            database.execSQL("ALTER TABLE invoices ADD COLUMN returnedAmount INTEGER NOT NULL "
                    + "DEFAULT 0");
            database.execSQL("ALTER TABLE invoices ADD COLUMN directCosts INTEGER NOT NULL "
                    + "DEFAULT 0");
            database.execSQL("ALTER TABLE invoices ADD COLUMN paymentStatus TEXT NOT NULL "
                    + "DEFAULT 'UNPAID'");
            database.execSQL("UPDATE invoices SET paymentStatus = CASE "
                    + "WHEN paid >= total THEN 'PAID' "
                    + "WHEN paid > 0 THEN 'PARTIAL' ELSE 'UNPAID' END");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_originalInvoiceId "
                    + "ON invoices(originalInvoiceId)");

            database.execSQL("ALTER TABLE invoice_lines ADD COLUMN originalLineId TEXT "
                    + "REFERENCES invoice_lines(lineId) ON UPDATE NO ACTION ON DELETE RESTRICT");
            database.execSQL("ALTER TABLE invoice_lines ADD COLUMN restock INTEGER NOT NULL "
                    + "DEFAULT 1");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_originalLineId "
                    + "ON invoice_lines(originalLineId)");

            database.execSQL("ALTER TABLE cash_boxes ADD COLUMN currencyCode TEXT NOT NULL "
                    + "DEFAULT 'YER'");
            database.execSQL("ALTER TABLE payments ADD COLUMN currencyCode TEXT NOT NULL "
                    + "DEFAULT 'YER'");
            database.execSQL("ALTER TABLE expenses ADD COLUMN currencyCode TEXT NOT NULL "
                    + "DEFAULT 'YER'");
            database.execSQL("ALTER TABLE inventory_items ADD COLUMN currencyCode TEXT NOT NULL "
                    + "DEFAULT 'YER'");
            database.execSQL("ALTER TABLE stock_movements ADD COLUMN currencyCode TEXT NOT NULL "
                    + "DEFAULT 'YER'");

            database.execSQL("CREATE TABLE IF NOT EXISTS owner_withdrawals ("
                    + "withdrawalId TEXT NOT NULL PRIMARY KEY, idempotencyKey TEXT NOT NULL, "
                    + "kind TEXT NOT NULL, itemId TEXT, cashBoxId TEXT, journalEntryId TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, quantity INTEGER NOT NULL, "
                    + "currencyCode TEXT NOT NULL DEFAULT 'YER', createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(itemId) REFERENCES inventory_items(itemId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(cashBoxId) REFERENCES cash_boxes(cashBoxId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT, "
                    + "FOREIGN KEY(journalEntryId) REFERENCES journal_entries(entryId) "
                    + "ON UPDATE NO ACTION ON DELETE RESTRICT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_owner_withdrawals_idempotencyKey "
                    + "ON owner_withdrawals(idempotencyKey)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_owner_withdrawals_itemId "
                    + "ON owner_withdrawals(itemId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_owner_withdrawals_cashBoxId "
                    + "ON owner_withdrawals(cashBoxId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_owner_withdrawals_journalEntryId "
                    + "ON owner_withdrawals(journalEntryId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_owner_withdrawals_createdAt "
                    + "ON owner_withdrawals(createdAt)");
            installJournalIntegrityTriggers(database);
        }
    };

    private static void installJournalIntegrityTriggers(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TRIGGER IF NOT EXISTS validate_posted_journal_insert "
                + "BEFORE INSERT ON journal_entries WHEN NEW.isPosted = 1 "
                + "BEGIN SELECT RAISE(ABORT, 'journal must be posted after balanced lines'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS validate_posted_journal_update "
                + "BEFORE UPDATE OF isPosted ON journal_entries WHEN NEW.isPosted = 1 AND "
                + "(NEW.totalDebit <= 0 OR NEW.totalDebit != NEW.totalCredit OR "
                + "(SELECT COALESCE(SUM(debit), 0) FROM journal_lines "
                + "WHERE entryId = NEW.entryId) != NEW.totalDebit OR "
                + "(SELECT COALESCE(SUM(credit), 0) FROM journal_lines "
                + "WHERE entryId = NEW.entryId) != NEW.totalCredit) "
                + "BEGIN SELECT RAISE(ABORT, 'unbalanced journal'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_posted_journal_update "
                + "BEFORE UPDATE ON journal_entries WHEN OLD.isPosted = 1 "
                + "BEGIN SELECT RAISE(ABORT, 'posted journal is immutable'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_posted_journal_delete "
                + "BEFORE DELETE ON journal_entries WHEN OLD.isPosted = 1 "
                + "BEGIN SELECT RAISE(ABORT, 'posted journal is immutable'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_posted_line_insert "
                + "BEFORE INSERT ON journal_lines WHEN EXISTS (SELECT 1 FROM journal_entries "
                + "WHERE entryId = NEW.entryId AND isPosted = 1) "
                + "BEGIN SELECT RAISE(ABORT, 'posted journal lines are immutable'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_posted_line_update "
                + "BEFORE UPDATE ON journal_lines WHEN EXISTS (SELECT 1 FROM journal_entries "
                + "WHERE entryId = OLD.entryId AND isPosted = 1) OR EXISTS "
                + "(SELECT 1 FROM journal_entries WHERE entryId = NEW.entryId AND isPosted = 1) "
                + "BEGIN SELECT RAISE(ABORT, 'posted journal lines are immutable'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_posted_line_delete "
                + "BEFORE DELETE ON journal_lines WHEN EXISTS (SELECT 1 FROM journal_entries "
                + "WHERE entryId = OLD.entryId AND isPosted = 1) "
                + "BEGIN SELECT RAISE(ABORT, 'posted journal lines are immutable'); END");
    }
}
