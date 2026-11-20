package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.UUID;

public final class OwnerWithdrawalService {
    private final AppDatabase database;

    public OwnerWithdrawalService(AppDatabase database) {
        this.database = database;
    }

    public OwnerWithdrawal withdrawCash(long amount, String currencyCode,
                                        String idempotencyKey) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_WITHDRAWAL);
        validate(amount, currencyCode, idempotencyKey);
        OwnerWithdrawal[] result = new OwnerWithdrawal[1];
        database.runInTransaction(() -> {
            OwnerWithdrawal previous = database.erpDao()
                    .getOwnerWithdrawalByIdempotencyKey(idempotencyKey);
            if (previous != null) {
                if (!OwnerWithdrawal.KIND_CASH.equals(previous.kind)
                        || previous.amount != amount
                        || !currencyCode.equals(previous.currencyCode)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لمسحوب مختلف");
                }
                result[0] = previous;
                return;
            }
            long timestamp = System.currentTimeMillis();
            if (database.erpDao().getCashBalance(currencyCode) < amount) {
                throw new IllegalStateException("رصيد الصندوق غير كافٍ للمسحوب");
            }
            CashBox cashBox = AccountingAccounts.cashBox(database, currencyCode, timestamp);
            String cashAccount = AccountingAccounts.account(database, "system-cash",
                    "الصندوق", "ASSET", currencyCode);
            String drawingsAccount = drawingsAccount(currencyCode);
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "مسحوبات مالك نقدية", "OWNER_WITHDRAWAL", idempotencyKey, amount);
            OwnerWithdrawal withdrawal = new OwnerWithdrawal();
            withdrawal.withdrawalId = UUID.randomUUID().toString();
            withdrawal.idempotencyKey = idempotencyKey;
            withdrawal.kind = OwnerWithdrawal.KIND_CASH;
            withdrawal.cashBoxId = cashBox.cashBoxId;
            withdrawal.journalEntryId = entry.entryId;
            withdrawal.amount = amount;
            withdrawal.currencyCode = currencyCode;
            withdrawal.createdAt = timestamp;
            database.erpDao().insertOwnerWithdrawal(withdrawal);

            CashMovement movement = new CashMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.cashBoxId = cashBox.cashBoxId;
            movement.referenceType = "OWNER_WITHDRAWAL";
            movement.referenceId = withdrawal.withdrawalId;
            movement.amount = -amount;
            movement.createdAt = timestamp;
            database.erpDao().insertCashMovement(movement);

            AccountingEngine.postLines(database, entry, Arrays.asList(
                    AccountingAccounts.line(entry.entryId, drawingsAccount, null, amount, 0),
                    AccountingAccounts.line(entry.entryId, cashAccount, null, 0, amount)));
            AccountingAccounts.audit(database, actor.userId, "POST", "OWNER_WITHDRAWAL",
                    withdrawal.withdrawalId, "مسحوبات مالك نقدية", timestamp);
            result[0] = withdrawal;
        });
        return result[0];
    }

    public OwnerWithdrawal withdrawInventory(String itemId, long quantity,
                                             String idempotencyKey) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_WITHDRAWAL);
        if (itemId == null || itemId.trim().isEmpty() || quantity <= 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("الصنف والكمية ومفتاح العملية مطلوبة");
        }
        OwnerWithdrawal[] result = new OwnerWithdrawal[1];
        database.runInTransaction(() -> {
            OwnerWithdrawal previous = database.erpDao()
                    .getOwnerWithdrawalByIdempotencyKey(idempotencyKey);
            if (previous != null) {
                if (!OwnerWithdrawal.KIND_IN_KIND.equals(previous.kind)
                        || !itemId.equals(previous.itemId) || previous.quantity != quantity) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لمسحوب مختلف");
                }
                result[0] = previous;
                return;
            }
            InventoryItem item = database.inventoryDao().getItem(itemId);
            if (item == null || !item.isActive || item.quantity < quantity
                    || item.purchasePriceAverage <= 0) {
                throw new IllegalStateException("الصنف غير موجود أو المخزون غير كافٍ");
            }
            String currencyCode = MoneyCurrency.requireSupported(item.currencyCode);
            long amount = FinancialMath.multiply(item.purchasePriceAverage, quantity);
            long timestamp = System.currentTimeMillis();
            String inventoryAccount = AccountingAccounts.account(database,
                    "system-inventory", "المخزون", "ASSET", currencyCode);
            String drawingsAccount = drawingsAccount(currencyCode);
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "مسحوبات مالك عينية " + item.itemName,
                    "OWNER_WITHDRAWAL", idempotencyKey, amount);
            if (database.inventoryDao().decreaseStock(itemId, quantity) != 1) {
                throw new IllegalStateException("تغير المخزون؛ لم يُعتمد المسحوب");
            }
            OwnerWithdrawal withdrawal = new OwnerWithdrawal();
            withdrawal.withdrawalId = UUID.randomUUID().toString();
            withdrawal.idempotencyKey = idempotencyKey;
            withdrawal.kind = OwnerWithdrawal.KIND_IN_KIND;
            withdrawal.itemId = itemId;
            withdrawal.journalEntryId = entry.entryId;
            withdrawal.amount = amount;
            withdrawal.quantity = quantity;
            withdrawal.currencyCode = currencyCode;
            withdrawal.createdAt = timestamp;
            database.erpDao().insertOwnerWithdrawal(withdrawal);

            StockMovement movement = new StockMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.itemId = itemId;
            movement.referenceId = withdrawal.withdrawalId;
            movement.movementType = "OWNER_WITHDRAWAL";
            movement.quantityChange = -quantity;
            movement.costAtTheTime = item.purchasePriceAverage;
            movement.currencyCode = currencyCode;
            movement.date = AppDateTime.formatUtc(timestamp);
            database.inventoryDao().insertMovement(movement);

            AccountingEngine.postLines(database, entry, Arrays.asList(
                    AccountingAccounts.line(entry.entryId, drawingsAccount, null, amount, 0),
                    AccountingAccounts.line(entry.entryId, inventoryAccount, null, 0, amount)));
            AccountingAccounts.audit(database, actor.userId, "POST", "OWNER_WITHDRAWAL",
                    withdrawal.withdrawalId, "مسحوبات مالك عينية، كمية " + quantity, timestamp);
            result[0] = withdrawal;
        });
        return result[0];
    }

    private String drawingsAccount(String currencyCode) {
        return AccountingAccounts.account(database, "system-owner-drawings",
                "مسحوبات المالك", "EQUITY", currencyCode);
    }

    private static void validate(long amount, String currencyCode, String idempotencyKey) {
        if (amount <= 0 || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("المبلغ ومفتاح العملية مطلوبان");
        }
        MoneyCurrency.requireSupported(currencyCode);
    }
}
