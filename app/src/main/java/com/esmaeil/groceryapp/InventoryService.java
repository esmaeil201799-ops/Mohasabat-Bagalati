package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.UUID;

public final class InventoryService {
    private final AppDatabase database;

    public InventoryService(AppDatabase database) {
        this.database = database;
    }

    public void postOpeningStock(String itemId, long quantity, long unitCost,
                                 String idempotencyKey) {
        AppUser actor = SessionManager.require(
                database, SessionManager.Permission.MANAGE_INVENTORY);
        if (itemId == null || itemId.trim().isEmpty() || quantity <= 0 || unitCost <= 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("الصنف والكمية والتكلفة ومفتاح العملية مطلوبة");
        }

        database.runInTransaction(() -> {
            StockMovement previous = database.inventoryDao().getMovementByReference(
                    idempotencyKey, "OPENING_STOCK");
            if (previous != null) {
                if (!itemId.equals(previous.itemId) || previous.quantityChange != quantity
                        || previous.costAtTheTime != unitCost) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لإدخال مخزون مختلف");
                }
                return;
            }

            InventoryItem item = database.inventoryDao().getItem(itemId);
            if (item == null || !item.isActive) {
                throw new IllegalArgumentException("الصنف غير موجود أو غير نشط");
            }
            long newQuantity = FinancialMath.add(item.quantity, quantity);
            long newAverage = InventoryEngine.calculateNewWeightedAverage(item.quantity,
                    item.purchasePriceAverage, quantity, unitCost, 0);
            long value = FinancialMath.multiply(quantity, unitCost);
            String currencyCode = MoneyCurrency.requireSupported(item.currencyCode);

            String inventoryAccount = MoneyCurrency.accountId("system-inventory", currencyCode);
            String equityAccount = MoneyCurrency.accountId("system-opening-equity", currencyCode);
            ensureAccount(inventoryAccount, "المخزون", "ASSET", currencyCode);
            ensureAccount(equityAccount, "رصيد المخزون الافتتاحي", "EQUITY", currencyCode);

            JournalEntry entry = new JournalEntry();
            entry.entryId = UUID.randomUUID().toString();
            entry.date = AppDateTime.formatUtc(System.currentTimeMillis());
            entry.description = "إثبات رصيد مخزون افتتاحي للصنف " + item.itemName;
            entry.referenceType = "OPENING_STOCK";
            entry.referenceId = idempotencyKey;
            entry.totalDebit = value;
            entry.totalCredit = value;
            entry.isPosted = false;
            database.accountDao().insertJournalEntry(entry);

            JournalLine debit = journalLine(entry.entryId, inventoryAccount, value, 0);
            JournalLine credit = journalLine(entry.entryId, equityAccount, 0, value);
            AccountingEngine.postLines(database, entry, Arrays.asList(debit, credit));

            if (database.inventoryDao().updateStockAndAverageCost(
                    itemId, newQuantity, newAverage) != 1) {
                throw new IllegalStateException("تعذر تحديث المخزون؛ لم يتم اعتماد العملية");
            }
            StockMovement movement = new StockMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.itemId = itemId;
            movement.referenceId = idempotencyKey;
            movement.movementType = "OPENING_STOCK";
            movement.quantityChange = quantity;
            movement.costAtTheTime = unitCost;
            movement.currencyCode = currencyCode;
            movement.date = entry.date;
            database.inventoryDao().insertMovement(movement);

            AuditEvent audit = new AuditEvent();
            audit.eventId = UUID.randomUUID().toString();
            audit.userId = actor.userId;
            audit.action = "POST";
            audit.entityType = "STOCK_MOVEMENT";
            audit.entityId = movement.movementId;
            audit.details = "إثبات رصيد افتتاحي، الكمية " + quantity + "، التكلفة " + unitCost;
            audit.createdAt = System.currentTimeMillis();
            database.erpDao().insertAuditEvent(audit);
        });
    }

    private void ensureAccount(String id, String name, String type, String currencyCode) {
        if (database.accountDao().getAccount(id) != null) {
            return;
        }
        Account account = new Account();
        account.accountId = id;
        account.accountName = name + " (" + currencyCode + ")";
        account.accountType = type;
        database.accountDao().insertAccountIfMissing(account);
    }

    private static JournalLine journalLine(String entryId, String accountId,
                                           long debitAmount, long creditAmount) {
        JournalLine line = new JournalLine();
        line.lineId = UUID.randomUUID().toString();
        line.entryId = entryId;
        line.accountId = accountId;
        line.debit = debitAmount;
        line.credit = creditAmount;
        return line;
    }
}
