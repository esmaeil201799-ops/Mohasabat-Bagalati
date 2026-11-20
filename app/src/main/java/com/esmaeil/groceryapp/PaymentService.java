package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.UUID;

public final class PaymentService {
    private final AppDatabase database;

    public PaymentService(AppDatabase database) {
        this.database = database;
    }

    public Payment postCustomerReceipt(String invoiceId, long amount, String idempotencyKey) {
        return postInvoicePayment(invoiceId, amount, idempotencyKey, true);
    }

    public Payment postSupplierPayment(String invoiceId, long amount, String idempotencyKey) {
        return postInvoicePayment(invoiceId, amount, idempotencyKey, false);
    }

    private Payment postInvoicePayment(String invoiceId, long amount, String idempotencyKey,
                                       boolean receipt) {
        SessionManager.Permission permission = receipt
                ? SessionManager.Permission.RECEIVE_PAYMENT
                : SessionManager.Permission.PAY_SUPPLIER;
        AppUser actor = SessionManager.require(database, permission);
        if (invoiceId == null || invoiceId.trim().isEmpty() || amount <= 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("الفاتورة والمبلغ ومفتاح العملية مطلوبة");
        }
        Payment[] result = new Payment[1];
        database.runInTransaction(() -> {
            Payment previous = database.erpDao().getPaymentByIdempotencyKey(idempotencyKey);
            if (previous != null) {
                if (!invoiceId.equals(previous.invoiceId) || previous.amount != amount
                        || !(receipt ? "IN" : "OUT").equals(previous.direction)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لسند مختلف");
                }
                result[0] = previous;
                return;
            }

            Invoice invoice = database.erpDao().getInvoice(invoiceId);
            if (invoice == null || !"POSTED".equals(invoice.status)
                    || !(receipt ? ("SALE_CASH".equals(invoice.type)
                    || "SALE_CREDIT".equals(invoice.type))
                    : "PURCHASE".equals(invoice.type))) {
                throw new IllegalArgumentException("نوع الفاتورة غير مناسب للسند");
            }
            if (invoice.total < 0 || invoice.paid < 0 || invoice.returnedAmount < 0
                    || invoice.paid > invoice.total
                    || invoice.returnedAmount > invoice.total - invoice.paid) {
                throw new IllegalStateException("أرصدة الفاتورة غير متزنة");
            }
            long outstanding = invoice.total - invoice.paid - invoice.returnedAmount;
            if (amount > outstanding) {
                throw new IllegalArgumentException("المبلغ يتجاوز الرصيد المستحق على الفاتورة");
            }
            long timestamp = System.currentTimeMillis();
            if (!receipt && database.erpDao().getCashBalance(invoice.currencyCode) < amount) {
                throw new IllegalStateException("رصيد الصندوق غير كافٍ للسداد");
            }
            CashBox cashBox = AccountingAccounts.cashBox(
                    database, invoice.currencyCode, timestamp);
            String cashAccount = AccountingAccounts.account(database, "system-cash",
                    "الصندوق", "ASSET", invoice.currencyCode);
            String partnerAccount = AccountingAccounts.account(database,
                    receipt ? "system-receivables" : "system-payables",
                    receipt ? "ذمم العملاء" : "ذمم الموردين",
                    receipt ? "ASSET" : "LIABILITY", invoice.currencyCode);
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    receipt ? "سند قبض " + idempotencyKey : "سند سداد " + idempotencyKey,
                    receipt ? "RECEIPT" : "SUPPLIER_PAYMENT", idempotencyKey, amount);
            String entryId = entry.entryId;
            JournalLine cashLine = AccountingAccounts.line(entryId, cashAccount, null,
                    receipt ? amount : 0, receipt ? 0 : amount);
            JournalLine partnerLine = AccountingAccounts.line(entryId, partnerAccount,
                    invoice.partnerId, receipt ? 0 : amount, receipt ? amount : 0);

            Payment payment = new Payment();
            payment.paymentId = UUID.randomUUID().toString();
            payment.idempotencyKey = idempotencyKey;
            payment.direction = receipt ? "IN" : "OUT";
            payment.currencyCode = invoice.currencyCode;
            payment.invoiceId = invoice.invoiceId;
            payment.partnerId = invoice.partnerId;
            payment.cashBoxId = cashBox.cashBoxId;
            payment.journalEntryId = entryId;
            payment.amount = amount;
            payment.createdAt = timestamp;
            database.erpDao().insertPayment(payment);

            CashMovement movement = new CashMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.cashBoxId = cashBox.cashBoxId;
            movement.referenceType = receipt ? "RECEIPT" : "SUPPLIER_PAYMENT";
            movement.referenceId = payment.paymentId;
            movement.amount = receipt ? amount : -amount;
            movement.createdAt = timestamp;
            database.erpDao().insertCashMovement(movement);

            invoice.paid = FinancialMath.add(invoice.paid, amount);
            invoice.paymentStatus = invoice.paid >= invoice.total - invoice.returnedAmount
                    ? "PAID" : "PARTIAL";
            if (database.erpDao().updateInvoice(invoice) != 1) {
                throw new IllegalStateException("تعذر تحديث رصيد الفاتورة");
            }
            AccountingEngine.postLines(database, entry, Arrays.asList(cashLine, partnerLine));
            AccountingAccounts.audit(database, actor.userId, "POST", "PAYMENT",
                    payment.paymentId, receipt ? "تسجيل سند قبض" : "تسجيل سند سداد", timestamp);
            result[0] = payment;
        });
        return result[0];
    }
}
