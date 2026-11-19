package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.UUID;

public final class CashOpeningService {
    private final AppDatabase database;

    public CashOpeningService(AppDatabase database) {
        this.database = database;
    }

    public void postOpeningCash(long amount, String currencyCode, String idempotencyKey) {
        AppUser actor = SessionManager.require(
                database, SessionManager.Permission.MANAGE_INVENTORY);
        if (amount <= 0 || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("مبلغ الافتتاح ومفتاح العملية مطلوبان");
        }
        MoneyCurrency.requireSupported(currencyCode);
        database.runInTransaction(() -> {
            String settingKey = "opening_cash:" + currencyCode;
            String storedKey = database.erpDao().getSetting(settingKey);
            if (storedKey != null) {
                if (storedKey.equals(idempotencyKey)) {
                    return;
                }
                throw new IllegalStateException("تم إثبات رصيد افتتاحي لهذه العملة مسبقًا");
            }
            if (database.erpDao().getCashBalance(currencyCode) != 0) {
                throw new IllegalStateException("توجد حركة صندوق؛ لا يمكن إثبات رصيد افتتاحي جديد");
            }
            long timestamp = System.currentTimeMillis();
            CashBox cashBox = AccountingAccounts.cashBox(database, currencyCode, timestamp);
            String cashAccount = AccountingAccounts.account(database, "system-cash",
                    "الصندوق", "ASSET", currencyCode);
            String openingEquity = AccountingAccounts.account(database,
                    "system-opening-equity", "رصيد افتتاحي", "EQUITY", currencyCode);
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "رصيد نقدي افتتاحي", "OPENING_CASH", idempotencyKey, amount);
            CashMovement movement = new CashMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.cashBoxId = cashBox.cashBoxId;
            movement.referenceType = "OPENING_CASH";
            movement.referenceId = idempotencyKey;
            movement.amount = amount;
            movement.createdAt = timestamp;
            database.erpDao().insertCashMovement(movement);

            AppSetting setting = new AppSetting();
            setting.settingKey = settingKey;
            setting.settingValue = idempotencyKey;
            database.erpDao().insertAppSetting(setting);
            AccountingEngine.postLines(database, entry, Arrays.asList(
                    AccountingAccounts.line(entry.entryId, cashAccount, null, amount, 0),
                    AccountingAccounts.line(entry.entryId, openingEquity, null, 0, amount)));
            AccountingAccounts.audit(database, actor.userId, "POST", "OPENING_CASH",
                    movement.movementId, "إثبات رصيد صندوق افتتاحي " + currencyCode, timestamp);
        });
    }
}
