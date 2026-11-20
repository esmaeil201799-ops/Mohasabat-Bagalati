package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.UUID;

public final class OpeningBalanceService {
    public static final String CUSTOMER_RECEIVABLE = "OPENING_RECEIVABLE";
    public static final String SUPPLIER_PAYABLE = "OPENING_PAYABLE";

    private final AppDatabase database;

    public OpeningBalanceService(AppDatabase database) {
        this.database = database;
    }

    public Invoice postPartnerBalance(String partnerId, String balanceType, long amount,
                                      String currencyCode, String idempotencyKey) {
        AppUser actor = SessionManager.require(
                database, SessionManager.Permission.MANAGE_PARTNERS);
        if (partnerId == null || partnerId.trim().isEmpty() || amount <= 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()
                || !(CUSTOMER_RECEIVABLE.equals(balanceType)
                || SUPPLIER_PAYABLE.equals(balanceType))) {
            throw new IllegalArgumentException("الشريك ونوع الرصيد والمبلغ ومفتاح العملية مطلوبة");
        }
        MoneyCurrency.requireSupported(currencyCode);
        String fingerprint = partnerId + "|" + balanceType + "|" + amount + "|" + currencyCode;
        Invoice[] result = new Invoice[1];
        database.runInTransaction(() -> {
            Invoice prior = database.erpDao().getInvoiceByIdempotencyKey(idempotencyKey);
            if (prior != null) {
                if (!fingerprint.equals(prior.requestFingerprint)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لرصيد افتتاحي مختلف");
                }
                result[0] = prior;
                return;
            }

            BusinessPartner partner = database.erpDao().getPartner(partnerId);
            String expectedType = CUSTOMER_RECEIVABLE.equals(balanceType)
                    ? BusinessPartner.TYPE_CUSTOMER : BusinessPartner.TYPE_SUPPLIER;
            if (partner == null || !partner.isActive || !expectedType.equals(partner.type)) {
                throw new IllegalArgumentException("الشريك غير موجود أو نوعه غير مناسب");
            }
            String settingKey = "opening_partner:" + balanceType + ":" + partnerId
                    + ":" + currencyCode;
            String previousKey = database.erpDao().getSetting(settingKey);
            if (previousKey != null) {
                throw new IllegalStateException("سبق تسجيل رصيد افتتاحي لهذا الشريك والعملة");
            }

            long timestamp = System.currentTimeMillis();
            String receivablesAccount = AccountingAccounts.account(database,
                    "system-receivables", "ذمم العملاء", "ASSET", currencyCode);
            String payablesAccount = AccountingAccounts.account(database,
                    "system-payables", "ذمم الموردين", "LIABILITY", currencyCode);
            String openingEquity = AccountingAccounts.account(database,
                    "system-opening-equity", "رصيد افتتاحي", "EQUITY", currencyCode);
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "رصيد افتتاحي للشريك " + partner.name, balanceType, idempotencyKey, amount);

            Invoice invoice = new Invoice();
            invoice.invoiceId = UUID.randomUUID().toString();
            invoice.idempotencyKey = idempotencyKey;
            invoice.requestFingerprint = fingerprint;
            invoice.type = balanceType;
            invoice.partnerId = partnerId;
            invoice.journalEntryId = entry.entryId;
            invoice.issuedAt = timestamp;
            invoice.currencyCode = currencyCode;
            invoice.subtotal = amount;
            invoice.total = amount;
            invoice.paid = 0;
            invoice.paymentStatus = "UNPAID";
            invoice.status = "POSTED";
            database.erpDao().insertInvoice(invoice);

            boolean receivable = CUSTOMER_RECEIVABLE.equals(balanceType);
            AccountingEngine.postLines(database, entry, Arrays.asList(
                    AccountingAccounts.line(entry.entryId,
                            receivable ? receivablesAccount : openingEquity,
                            receivable ? partnerId : null, amount, 0),
                    AccountingAccounts.line(entry.entryId,
                            receivable ? openingEquity : payablesAccount,
                            receivable ? null : partnerId, 0, amount)));

            AppSetting setting = new AppSetting();
            setting.settingKey = settingKey;
            setting.settingValue = idempotencyKey;
            database.erpDao().insertAppSetting(setting);
            AccountingAccounts.audit(database, actor.userId, "POST", "OPENING_BALANCE",
                    invoice.invoiceId, "إثبات رصيد " + balanceType + " بقيمة "
                            + amount + " " + currencyCode, timestamp);
            result[0] = invoice;
        });
        return result[0];
    }
}
