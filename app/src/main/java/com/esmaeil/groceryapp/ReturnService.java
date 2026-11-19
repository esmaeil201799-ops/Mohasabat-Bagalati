package com.esmaeil.groceryapp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ReturnService {
    private final AppDatabase database;

    public ReturnService(AppDatabase database) {
        this.database = database;
    }

    public Invoice returnSale(String originalInvoiceId, List<ReturnLine> lines,
                              String idempotencyKey) {
        return returnInvoice(originalInvoiceId, lines, idempotencyKey, true);
    }

    public Invoice returnPurchase(String originalInvoiceId, List<ReturnLine> lines,
                                  String idempotencyKey) {
        return returnInvoice(originalInvoiceId, lines, idempotencyKey, false);
    }

    private Invoice returnInvoice(String originalInvoiceId, List<ReturnLine> requestedLines,
                                  String idempotencyKey, boolean saleReturn) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_RETURN);
        if (originalInvoiceId == null || originalInvoiceId.trim().isEmpty()
                || requestedLines == null || requestedLines.isEmpty()
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("الفاتورة والأسطر ومفتاح العملية مطلوبة");
        }
        String fingerprint = fingerprint(originalInvoiceId, requestedLines, saleReturn);
        Invoice[] result = new Invoice[1];
        database.runInTransaction(() -> {
            Invoice duplicate = database.erpDao().getInvoiceByIdempotencyKey(idempotencyKey);
            if (duplicate != null) {
                if (!fingerprint.equals(duplicate.requestFingerprint)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لمرتجع مختلف");
                }
                result[0] = duplicate;
                return;
            }
            Invoice original = database.erpDao().getInvoice(originalInvoiceId);
            if (original == null || !"POSTED".equals(original.status)
                    || (saleReturn && !("SALE_CASH".equals(original.type)
                    || "SALE_CREDIT".equals(original.type)))
                    || (!saleReturn && !"PURCHASE".equals(original.type))
                    || original.originalInvoiceId != null) {
                throw new IllegalArgumentException("الفاتورة الأصلية غير صالحة لهذا المرتجع");
            }
            String currency = MoneyCurrency.requireSupported(original.currencyCode);
            long timestamp = System.currentTimeMillis();
            Set<String> seen = new HashSet<>();
            long total = 0;
            long inventoryValueChange = 0;
            List<InvoiceLine> returnLines = new ArrayList<>();
            List<StockMovement> movements = new ArrayList<>();

            for (ReturnLine request : requestedLines) {
                if (request == null || request.originalLineId == null
                        || request.originalLineId.trim().isEmpty() || request.quantity <= 0
                        || !seen.add(request.originalLineId)) {
                    throw new IllegalArgumentException("أسطر المرتجع غير صالحة أو مكررة");
                }
                InvoiceLine originalLine = database.erpDao()
                        .getInvoiceLine(request.originalLineId);
                if (originalLine == null || !originalInvoiceId.equals(originalLine.invoiceId)) {
                    throw new IllegalArgumentException("سطر المرتجع لا يتبع الفاتورة الأصلية");
                }
                long returnedBefore = database.erpDao()
                        .getReturnedQuantity(originalLine.lineId);
                if (FinancialMath.add(returnedBefore, request.quantity) > originalLine.quantity) {
                    throw new IllegalArgumentException("الكمية المرتجعة تتجاوز الكمية الأصلية المتبقية");
                }
                if (!saleReturn && request.restock) {
                    throw new IllegalArgumentException(
                            "مرتجع الشراء يخرج الصنف من مخزون المتجر ولا يُعاد تخزينه");
                }
                InventoryItem item = database.inventoryDao().getItem(originalLine.itemId);
                if (item == null || !currency.equals(item.currencyCode)) {
                    throw new IllegalStateException("الصنف أو عملته لا تطابق بيانات الفاتورة");
                }

                long lineValue = saleReturn
                        ? FinancialMath.multiply(originalLine.unitPrice, request.quantity)
                        : incrementalLineValue(originalLine, request.quantity,
                                returnedBefore, database.erpDao()
                                        .getReturnedLineTotal(originalLine.lineId));
                total = FinancialMath.add(total, lineValue);

                InvoiceLine returnLine = new InvoiceLine();
                returnLine.lineId = UUID.randomUUID().toString();
                returnLine.invoiceId = "pending";
                returnLine.originalLineId = originalLine.lineId;
                returnLine.itemId = originalLine.itemId;
                returnLine.quantity = request.quantity;
                returnLine.unitPrice = originalLine.unitPrice;
                returnLine.transportPerUnit = originalLine.transportPerUnit;
                returnLine.costAtPosting = originalLine.costAtPosting;
                returnLine.lineTotal = lineValue;
                returnLine.restock = saleReturn && request.restock;
                returnLines.add(returnLine);

                if (saleReturn && request.restock) {
                    long oldValue = FinancialMath.multiply(
                            item.quantity, item.purchasePriceAverage);
                    long newQuantity = FinancialMath.add(item.quantity, request.quantity);
                    long newAverage = InventoryEngine.calculateNewWeightedAverage(
                            item.quantity, item.purchasePriceAverage, request.quantity,
                            originalLine.costAtPosting, 0);
                    long newValue = FinancialMath.multiply(newQuantity, newAverage);
                    long increase = newValue - oldValue;
                    inventoryValueChange = FinancialMath.add(inventoryValueChange, increase);
                    if (database.inventoryDao().updateStockAndAverageCost(
                            item.itemId, newQuantity, newAverage) != 1) {
                        throw new IllegalStateException("تعذر إعادة الصنف إلى المخزون");
                    }
                    StockMovement movement = stockMovement(item, request.quantity,
                            currency, timestamp);
                    movement.costAtTheTime = originalLine.costAtPosting;
                    movements.add(movement);
                } else if (!saleReturn) {
                    if (item.quantity < request.quantity) {
                        throw new IllegalStateException("المخزون الحالي لا يكفي لإرجاع الصنف للمورد");
                    }
                    long oldValue = FinancialMath.multiply(
                            item.quantity, item.purchasePriceAverage);
                    long newQuantity = item.quantity - request.quantity;
                    long newAverage = newQuantity == 0 ? 0 : item.purchasePriceAverage;
                    long newValue = FinancialMath.multiply(newQuantity, newAverage);
                    inventoryValueChange = FinancialMath.add(
                            inventoryValueChange, oldValue - newValue);
                    if (database.inventoryDao().updateStockAndAverageCost(
                            item.itemId, newQuantity, newAverage) != 1) {
                        throw new IllegalStateException("تعذر خصم الصنف المرتجع للمورد");
                    }
                    StockMovement movement = stockMovement(item, -request.quantity,
                            currency, timestamp);
                    movement.movementType = "PURCHASE_RETURN";
                    movements.add(movement);
                }
            }
            if (total <= 0) {
                throw new IllegalArgumentException("قيمة المرتجع يجب أن تكون موجبة");
            }
            if (original.total < 0 || original.paid < 0 || original.returnedAmount < 0
                    || original.paid > original.total
                    || original.returnedAmount > original.total - original.paid) {
                throw new IllegalStateException("رصيد الفاتورة الأصلية غير متزن");
            }
            long outstanding = original.total - original.paid - original.returnedAmount;
            long partnerAdjustment = Math.min(total, outstanding);
            long cashAmount = total - partnerAdjustment;
            if (saleReturn && cashAmount > database.erpDao().getCashBalance(currency)) {
                throw new IllegalStateException("رصيد الصندوق لا يكفي لرد المبلغ");
            }

            CashBox cashBox = cashAmount > 0
                    ? AccountingAccounts.cashBox(database, currency, timestamp) : null;
            String invoiceId = UUID.randomUUID().toString();
            Invoice returnInvoice = new Invoice();
            returnInvoice.invoiceId = invoiceId;
            returnInvoice.idempotencyKey = idempotencyKey;
            returnInvoice.requestFingerprint = fingerprint;
            returnInvoice.type = saleReturn ? "SALE_RETURN" : "PURCHASE_RETURN";
            returnInvoice.originalInvoiceId = originalInvoiceId;
            returnInvoice.partnerId = original.partnerId;
            returnInvoice.cashBoxId = cashBox == null ? null : cashBox.cashBoxId;
            returnInvoice.issuedAt = timestamp;
            returnInvoice.currencyCode = currency;
            returnInvoice.subtotal = total;
            returnInvoice.total = total;
            returnInvoice.paid = cashAmount;
            returnInvoice.paymentStatus = "PAID";
            returnInvoice.status = "POSTED";

            String partnerAccount = AccountingAccounts.account(database,
                    saleReturn ? "system-receivables" : "system-payables",
                    saleReturn ? "ذمم العملاء" : "ذمم الموردين",
                    saleReturn ? "ASSET" : "LIABILITY", currency);
            String cashAccount = cashAmount == 0 ? null
                    : AccountingAccounts.account(database, "system-cash",
                            "الصندوق", "ASSET", currency);
            String differenceAccount = AccountingAccounts.account(database,
                    saleReturn ? "system-sales-returns" : "system-purchase-return-difference",
                    saleReturn ? "مردودات المبيعات" : "فروق مردودات المشتريات",
                    "EXPENSE", currency);
            long entryAmount = saleReturn
                    ? FinancialMath.add(total, inventoryValueChange)
                    : Math.max(total, inventoryValueChange);
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "مرتجع " + invoiceId, returnInvoice.type, invoiceId, entryAmount);
            returnInvoice.journalEntryId = entry.entryId;
            for (InvoiceLine line : returnLines) {
                line.invoiceId = invoiceId;
            }
            database.erpDao().insertInvoice(returnInvoice);
            database.erpDao().insertInvoiceLines(returnLines);
            for (StockMovement movement : movements) {
                movement.referenceId = invoiceId;
                database.inventoryDao().insertMovement(movement);
            }
            List<JournalLine> journalLines = new ArrayList<>();
            if (saleReturn) {
                journalLines.add(AccountingAccounts.line(
                        entry.entryId, differenceAccount, null, total, 0));
                if (partnerAdjustment > 0) {
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, partnerAccount, original.partnerId,
                            0, partnerAdjustment));
                }
                if (cashAmount > 0) {
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, cashAccount, null, 0, cashAmount));
                }
                if (inventoryValueChange > 0) {
                    String inventoryAccount = AccountingAccounts.account(database,
                            "system-inventory", "المخزون", "ASSET", currency);
                    String cogsAccount = AccountingAccounts.account(database,
                            "system-cost-of-sales", "تكلفة البضاعة المباعة", "EXPENSE", currency);
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, inventoryAccount, null, inventoryValueChange, 0));
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, cogsAccount, null, 0, inventoryValueChange));
                }
            } else {
                if (partnerAdjustment > 0) {
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, partnerAccount, original.partnerId,
                            partnerAdjustment, 0));
                }
                if (cashAmount > 0) {
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, cashAccount, null, cashAmount, 0));
                }
                String inventoryAccount = AccountingAccounts.account(database,
                        "system-inventory", "المخزون", "ASSET", currency);
                if (inventoryValueChange > 0) {
                    journalLines.add(AccountingAccounts.line(
                            entry.entryId, inventoryAccount, null, 0, inventoryValueChange));
                }
                long difference = Math.abs(total - inventoryValueChange);
                if (difference > 0) {
                    journalLines.add(AccountingAccounts.line(entry.entryId, differenceAccount,
                            null, inventoryValueChange > total ? difference : 0,
                            total > inventoryValueChange ? difference : 0));
                }
            }
            if (cashAmount > 0) {
                Payment payment = new Payment();
                payment.paymentId = UUID.randomUUID().toString();
                payment.idempotencyKey = (saleReturn ? "sale-return:" : "purchase-return:")
                        + idempotencyKey;
                payment.direction = saleReturn ? "OUT" : "IN";
                payment.currencyCode = currency;
                payment.invoiceId = invoiceId;
                payment.partnerId = original.partnerId;
                payment.cashBoxId = cashBox.cashBoxId;
                payment.journalEntryId = entry.entryId;
                payment.amount = cashAmount;
                payment.createdAt = timestamp;
                database.erpDao().insertPayment(payment);

                CashMovement cashMovement = new CashMovement();
                cashMovement.movementId = UUID.randomUUID().toString();
                cashMovement.cashBoxId = cashBox.cashBoxId;
                cashMovement.referenceType = returnInvoice.type;
                cashMovement.referenceId = invoiceId;
                cashMovement.amount = saleReturn ? -cashAmount : cashAmount;
                cashMovement.createdAt = timestamp;
                database.erpDao().insertCashMovement(cashMovement);
            }
            original.returnedAmount = FinancialMath.add(original.returnedAmount, total);
            if (original.returnedAmount >= original.total) {
                original.paymentStatus = "RETURNED";
            } else if (original.paid > 0 || original.returnedAmount > 0) {
                original.paymentStatus = original.paid
                        >= original.total - original.returnedAmount
                        ? "PAID" : "PARTIAL";
            }
            database.erpDao().updateInvoice(original);
            AccountingEngine.postLines(database, entry, journalLines);
            AccountingAccounts.audit(database, actor.userId, "POST", returnInvoice.type,
                    invoiceId, "اعتماد مرتجع " + originalInvoiceId, timestamp);
            result[0] = returnInvoice;
        });
        return result[0];
    }

    private static StockMovement stockMovement(InventoryItem item, long quantity,
                                               String currency,
                                               long timestamp) {
        StockMovement movement = new StockMovement();
        movement.movementId = UUID.randomUUID().toString();
        movement.itemId = item.itemId;
        movement.movementType = "SALE_RETURN";
        movement.quantityChange = quantity;
        movement.costAtTheTime = item.purchasePriceAverage;
        movement.currencyCode = currency;
        movement.date = AppDateTime.formatUtc(timestamp);
        return movement;
    }

    private static long incrementalLineValue(InvoiceLine original, long quantity,
                                             long returnedBefore, long returnedValueBefore) {
        long returnedAfter = FinancialMath.add(returnedBefore, quantity);
        long valueAfter = roundRatio(original.lineTotal, returnedAfter, original.quantity);
        return valueAfter - returnedValueBefore;
    }

    private static long roundRatio(long amount, long numerator, long denominator) {
        BigInteger[] result = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(numerator))
                .divideAndRemainder(BigInteger.valueOf(denominator));
        if (result[1].shiftLeft(1).compareTo(BigInteger.valueOf(denominator)) >= 0) {
            result[0] = result[0].add(BigInteger.ONE);
        }
        return FinancialMath.exactLong(result[0]);
    }

    private static String fingerprint(String originalId, List<ReturnLine> lines,
                                      boolean saleReturn) {
        StringBuilder value = new StringBuilder(originalId).append('|').append(saleReturn);
        for (ReturnLine line : lines) {
            if (line == null) {
                value.append("|null");
            } else {
                value.append('|').append(line.originalLineId).append(':')
                        .append(line.quantity).append(':').append(line.restock);
            }
        }
        return value.toString();
    }

    public static final class ReturnLine {
        public final String originalLineId;
        public final long quantity;
        public final boolean restock;

        public ReturnLine(String originalLineId, long quantity, boolean restock) {
            this.originalLineId = originalLineId;
            this.quantity = quantity;
            this.restock = restock;
        }
    }
}
