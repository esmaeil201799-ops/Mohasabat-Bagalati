package com.esmaeil.groceryapp;

import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountingOperationsActivity extends AppCompatActivity {
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.hasSession()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_accounting_operations);
        database = AppDatabase.open(getApplicationContext());
        findViewById(R.id.btnPostPurchase).setOnClickListener(v -> showPurchaseForm());
        findViewById(R.id.btnReceiveCustomer).setOnClickListener(v -> showInvoicePicker(true));
        findViewById(R.id.btnPaySupplier).setOnClickListener(v -> showInvoicePicker(false));
        findViewById(R.id.btnPostExpense).setOnClickListener(v -> showExpenseForm());
        findViewById(R.id.btnPostReturn).setOnClickListener(v -> showReturnTypePicker());
        findViewById(R.id.btnOwnerWithdrawal).setOnClickListener(v -> showWithdrawalTypePicker());
        findViewById(R.id.btnOpeningBalances).setOnClickListener(v -> showOpeningBalanceForm());
        findViewById(R.id.btnOperationsDone).setOnClickListener(v -> finish());
    }

    private void showPurchaseForm() {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.POST_PURCHASE);
                List<BusinessPartner> suppliers = database.erpDao().getSuppliers();
                List<InventoryItem> items = database.inventoryDao().getAllItems();
                runOnUiThread(() -> {
                    if (suppliers.isEmpty()) {
                        showMessage("أضف موردًا من شاشة العملاء والموردين أولًا");
                    } else if (items.isEmpty()) {
                        showMessage("أضف صنفًا نشطًا إلى المخزون أولًا");
                    } else {
                        showPurchaseForm(suppliers, items);
                    }
                });
            } catch (RuntimeException exception) {
                showError("تعذر بدء فاتورة الشراء", exception);
            }
        });
    }

    private void showPurchaseForm(List<BusinessPartner> suppliers, List<InventoryItem> items) {
        LinearLayout form = createForm();
        Spinner supplier = spinner(form, Arrays.asList(partnerNames(suppliers)));
        Spinner currency = spinner(form, MoneyCurrency.all());
        TextView linesSummary = new TextView(this);
        linesSummary.setText("لم تُضف أصناف بعد");
        form.addView(linesSummary);
        ArrayList<PurchaseService.PurchaseLine> lines = new ArrayList<>();
        ArrayList<String> lineNames = new ArrayList<>();
        Button addLine = new Button(this);
        addLine.setText("إضافة صنف للفاتورة");
        form.addView(addLine);
        EditText directCosts = addField(form, "النقل والمصاريف المباشرة (أصغر وحدة نقدية)", true);
        EditText paid = addField(form, "المبلغ المدفوع الآن (0 للشراء الآجل)", true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("فاتورة شراء متعددة الأصناف")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل الشراء", null)
                .create();
        addLine.setOnClickListener(v -> {
            String code = currency.getSelectedItem().toString();
            ArrayList<InventoryItem> eligible = new ArrayList<>();
            for (InventoryItem item : items) {
                if (code.equals(item.currencyCode)) {
                    eligible.add(item);
                }
            }
            if (eligible.isEmpty()) {
                showMessage("لا توجد أصناف نشطة بهذه العملة");
                return;
            }
            String[] labels = new String[eligible.size()];
            for (int i = 0; i < eligible.size(); i++) {
                InventoryItem item = eligible.get(i);
                labels[i] = item.itemName + " — المخزون: " + item.quantity;
            }
            new AlertDialog.Builder(this)
                    .setTitle("اختر الصنف")
                    .setItems(labels, (choiceDialog, index) ->
                            showPurchaseLineForm(eligible.get(index), lines, lineNames,
                                    linesSummary, currency))
                    .setNegativeButton("إلغاء", null)
                    .show();
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (lines.isEmpty()) {
                        showMessage("أضف صنفًا واحدًا على الأقل");
                        return;
                    }
                    long costs;
                    long amountPaid;
                    try {
                        costs = parseNonNegative(directCosts.getText().toString());
                        amountPaid = parseNonNegative(paid.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    String supplierId = suppliers.get(supplier.getSelectedItemPosition()).partnerId;
                    String currencyCode = currency.getSelectedItem().toString();
                    String fingerprint = purchaseFingerprint(
                            supplierId, currencyCode, lines, costs, amountPaid);
                    String operationKey;
                    try {
                        operationKey = pendingKey("purchase", fingerprint);
                    } catch (RuntimeException exception) {
                        showError("تعذر حفظ مفتاح منع التكرار", exception);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            Invoice invoice = new PurchaseService(database).postPurchase(
                                    supplierId, new ArrayList<>(lines), costs, amountPaid,
                                    currencyCode, operationKey);
                            boolean keyCleared = clearPendingKey("purchase", operationKey);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage("رُحّلت فاتورة الشراء " + invoice.invoiceId
                                        + " إلى Room" + pendingKeyWarning(keyCleared));
                            });
                        } catch (RuntimeException exception) {
                            showError("لم تُرحّل فاتورة الشراء", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private void showPurchaseLineForm(InventoryItem item,
                                      List<PurchaseService.PurchaseLine> lines,
                                      List<String> lineNames, TextView summary,
                                      Spinner currency) {
        LinearLayout form = createForm();
        EditText quantity = addField(form, "الكمية", true);
        EditText unitPrice = addField(form, "سعر الشراء للوحدة (أصغر وحدة نقدية)", true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.itemName)
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إضافة", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        long count = parsePositive(quantity.getText().toString());
                        long price = parsePositive(unitPrice.getText().toString());
                        lines.add(new PurchaseService.PurchaseLine(item.itemId, count, price));
                        lineNames.add(item.itemName + " × " + count + " — " + price);
                        StringBuilder text = new StringBuilder();
                        for (String line : lineNames) {
                            text.append(line).append('\n');
                        }
                        summary.setText(text.toString());
                        currency.setEnabled(false);
                        dialog.dismiss();
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                    }
                }));
        dialog.show();
    }

    private void showInvoicePicker(boolean receipt) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, receipt
                        ? SessionManager.Permission.RECEIVE_PAYMENT
                        : SessionManager.Permission.PAY_SUPPLIER);
                List<Invoice> invoices = database.erpDao().getOutstandingInvoicesByTypes(receipt
                        ? Arrays.asList("SALE_CASH", "SALE_CREDIT",
                                OpeningBalanceService.CUSTOMER_RECEIVABLE)
                        : Arrays.asList("PURCHASE", OpeningBalanceService.SUPPLIER_PAYABLE));
                ArrayList<Invoice> outstanding = new ArrayList<>();
                for (Invoice invoice : invoices) {
                    if (invoice.total > invoice.paid + invoice.returnedAmount) {
                        outstanding.add(invoice);
                    }
                }
                runOnUiThread(() -> {
                    if (outstanding.isEmpty()) {
                        showMessage(receipt ? "لا توجد ذمم عملاء مستحقة للتحصيل"
                                : "لا توجد ذمم موردين مستحقة للسداد");
                        return;
                    }
                    String[] labels = new String[outstanding.size()];
                    for (int i = 0; i < outstanding.size(); i++) {
                        Invoice invoice = outstanding.get(i);
                        labels[i] = invoice.type + " — متبقي "
                                + (invoice.total - invoice.paid - invoice.returnedAmount)
                                + " " + invoice.currencyCode + " — "
                                + invoice.invoiceId.substring(0, 8);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(receipt ? "اختر فاتورة العميل" : "اختر فاتورة المورد")
                            .setItems(labels, (dialog, which) ->
                                    showPaymentAmount(outstanding.get(which), receipt))
                            .setNegativeButton("إغلاق", null)
                            .show();
                });
            } catch (RuntimeException exception) {
                showError("تعذر قراءة الفواتير المستحقة", exception);
            }
        });
    }

    private void showPaymentAmount(Invoice invoice, boolean receipt) {
        long due = invoice.total - invoice.paid - invoice.returnedAmount;
        EditText amount = new EditText(this);
        amount.setHint("المبلغ (الحد الأقصى " + due + " " + invoice.currencyCode + ")");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(receipt ? "سند قبض" : "سند سداد")
                .setView(amount)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long value;
                    try {
                        value = parsePositive(amount.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    String fingerprint = invoice.invoiceId + "|" + value + "|" + receipt;
                    String operationKey;
                    try {
                        operationKey = pendingKey(receipt ? "receipt" : "supplier-payment",
                                fingerprint);
                    } catch (RuntimeException exception) {
                        showError("تعذر حفظ مفتاح منع التكرار", exception);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            Payment payment = receipt
                                    ? new PaymentService(database).postCustomerReceipt(
                                            invoice.invoiceId, value, operationKey)
                                    : new PaymentService(database).postSupplierPayment(
                                            invoice.invoiceId, value, operationKey);
                            boolean keyCleared = clearPendingKey(
                                    receipt ? "receipt" : "supplier-payment", operationKey);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage((receipt ? "تم ترحيل سند القبض " : "تم ترحيل سند السداد ")
                                        + payment.paymentId + pendingKeyWarning(keyCleared));
                            });
                        } catch (RuntimeException exception) {
                            showError("لم يُرحّل السند", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private void showExpenseForm() {
        LinearLayout form = createForm();
        EditText description = addField(form, "بيان المصروف", false);
        EditText amount = addField(form, "المبلغ (أصغر وحدة نقدية)", true);
        Spinner currency = spinner(form, MoneyCurrency.all());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مصروف تشغيلي")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل المصروف", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = description.getText().toString().trim();
                    String code = currency.getSelectedItem().toString();
                    long value;
                    try {
                        value = parsePositive(amount.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    if (text.isEmpty()) {
                        showMessage("بيان المصروف مطلوب");
                        return;
                    }
                    String fingerprint = text + "|" + value + "|" + code;
                    String operationKey;
                    try {
                        operationKey = pendingKey("expense", fingerprint);
                    } catch (RuntimeException exception) {
                        showError("تعذر حفظ مفتاح منع التكرار", exception);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            Expense expense = new ExpenseService(database).postExpense(
                                    text, value, code, operationKey);
                            boolean keyCleared = clearPendingKey("expense", operationKey);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage("رُحّل المصروف " + expense.expenseId
                                        + pendingKeyWarning(keyCleared));
                            });
                        } catch (RuntimeException exception) {
                            showError("لم يُرحّل المصروف", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private void showOpeningBalanceForm() {
        new AlertDialog.Builder(this)
                .setTitle("نوع الرصيد الافتتاحي")
                .setItems(new String[]{"صندوق YER", "صندوق SAR", "ذمم عميل", "ذمم مورد"},
                        (dialog, which) -> {
                            if (which == 0 || which == 1) {
                                showOpeningCashForm(which == 0
                                        ? MoneyCurrency.YER : MoneyCurrency.SAR);
                            } else if (which == 2) {
                                showPartnerOpeningBalanceForm(
                                        OpeningBalanceService.CUSTOMER_RECEIVABLE);
                            } else {
                                showPartnerOpeningBalanceForm(
                                        OpeningBalanceService.SUPPLIER_PAYABLE);
                            }
                        })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showOpeningCashForm(String currencyCode) {
        EditText amount = new EditText(this);
        amount.setHint("المبلغ الافتتاحي " + currencyCode);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("رصيد صندوق افتتاحي")
                .setView(amount)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    long value;
                    try {
                        value = parsePositive(amount.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    String fingerprint = currencyCode + "|" + value;
                    String operationKey = pendingKey("opening-cash", fingerprint);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            new CashOpeningService(database).postOpeningCash(
                                    value, currencyCode, operationKey);
                            runOnUiThread(() -> {
                                clearPendingKey("opening-cash", operationKey);
                                dialog.dismiss();
                                showMessage("تم ترحيل رصيد الصندوق الافتتاحي");
                            });
                        } catch (RuntimeException exception) {
                            showError("لم يُرحّل رصيد الصندوق الافتتاحي", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private void showPartnerOpeningBalanceForm(String balanceType) {
        String partnerType = OpeningBalanceService.CUSTOMER_RECEIVABLE.equals(balanceType)
                ? BusinessPartner.TYPE_CUSTOMER : BusinessPartner.TYPE_SUPPLIER;
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_PARTNERS);
                List<BusinessPartner> partners = partnerType.equals(BusinessPartner.TYPE_CUSTOMER)
                        ? database.erpDao().getCustomers() : database.erpDao().getSuppliers();
                runOnUiThread(() -> {
                    if (partners.isEmpty()) {
                        showMessage(partnerType.equals(BusinessPartner.TYPE_CUSTOMER)
                                ? "أضف عميلًا أولًا" : "أضف موردًا أولًا");
                        return;
                    }
                    LinearLayout form = createForm();
                    ArrayList<String> labels = new ArrayList<>();
                    for (BusinessPartner partner : partners) {
                        labels.add(partner.name);
                    }
                    Spinner partner = spinner(form, labels);
                    Spinner currency = spinner(form, MoneyCurrency.all());
                    EditText amount = addField(form, "الرصيد الافتتاحي (أصغر وحدة نقدية)", true);
                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle(partnerType.equals(BusinessPartner.TYPE_CUSTOMER)
                                    ? "ذمم عميل افتتاحية" : "ذمم مورد افتتاحية")
                            .setView(form)
                            .setNegativeButton("إلغاء", null)
                            .setPositiveButton("ترحيل", null)
                            .create();
                    dialog.setOnShowListener(ignored -> dialog
                            .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                                long value;
                                try {
                                    value = parsePositive(amount.getText().toString());
                                } catch (IllegalArgumentException exception) {
                                    showMessage(exception.getMessage());
                                    return;
                                }
                                BusinessPartner selected = partners.get(
                                        partner.getSelectedItemPosition());
                                String code = currency.getSelectedItem().toString();
                                String fingerprint = selected.partnerId + "|" + balanceType
                                        + "|" + value + "|" + code;
                                String operationKey = pendingKey("opening-partner", fingerprint);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                databaseExecutor.execute(() -> {
                                    try {
                                        Invoice invoice = new OpeningBalanceService(database)
                                                .postPartnerBalance(selected.partnerId,
                                                        balanceType, value, code, operationKey);
                                        boolean cleared = clearPendingKey(
                                                "opening-partner", operationKey);
                                        runOnUiThread(() -> {
                                            dialog.dismiss();
                                            showMessage("تم ترحيل الرصيد الافتتاحي "
                                                    + invoice.invoiceId
                                                    + (cleared ? "" : "؛ حُفظ مفتاح منع التكرار"));
                                        });
                                    } catch (RuntimeException exception) {
                                        showError("لم يُرحّل رصيد الشريك الافتتاحي", exception);
                                        runOnUiThread(() -> dialog.getButton(
                                                AlertDialog.BUTTON_POSITIVE).setEnabled(true));
                                    }
                                });
                            }));
                    dialog.show();
                });
            } catch (RuntimeException exception) {
                showError("تعذر تحميل الشركاء للأرصدة الافتتاحية", exception);
            }
        });
    }

    private void showReturnTypePicker() {
        new AlertDialog.Builder(this)
                .setTitle("نوع المرتجع")
                .setItems(new String[]{"مرتجع مبيعات", "مرتجع مشتريات"},
                        (dialog, which) -> showReturnInvoicePicker(which == 0))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showReturnInvoicePicker(boolean saleReturn) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.POST_RETURN);
                List<Invoice> invoices = database.erpDao().getRecentInvoicesByTypes(saleReturn
                        ? Arrays.asList("SALE_CASH", "SALE_CREDIT")
                        : Arrays.asList("PURCHASE"));
                runOnUiThread(() -> {
                    if (invoices.isEmpty()) {
                        showMessage("لا توجد فواتير مناسبة للمرتجع");
                        return;
                    }
                    String[] labels = new String[invoices.size()];
                    for (int i = 0; i < invoices.size(); i++) {
                        Invoice invoice = invoices.get(i);
                        labels[i] = invoice.type + " — " + invoice.total + " "
                                + invoice.currencyCode + " — "
                                + invoice.invoiceId.substring(0, 8);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("اختر الفاتورة الأصلية")
                            .setItems(labels, (dialog, which) ->
                                    showReturnLinePicker(invoices.get(which), saleReturn))
                            .setNegativeButton("إغلاق", null)
                            .show();
                });
            } catch (RuntimeException exception) {
                showError("تعذر قراءة الفواتير", exception);
            }
        });
    }

    private void showReturnLinePicker(Invoice invoice, boolean saleReturn) {
        databaseExecutor.execute(() -> {
            try {
                List<InvoiceLine> lines = database.erpDao().getInvoiceLines(invoice.invoiceId);
                ArrayList<InvoiceLine> returnable = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                for (InvoiceLine line : lines) {
                    long returned = database.erpDao().getReturnedQuantity(line.lineId);
                    long remaining = line.quantity - returned;
                    if (remaining > 0) {
                        returnable.add(line);
                        InventoryItem item = database.inventoryDao().getItem(line.itemId);
                        labels.add((item == null ? line.itemId : item.itemName)
                                + " — متاح للمرتجع: " + remaining);
                    }
                }
                runOnUiThread(() -> {
                    if (returnable.isEmpty()) {
                        showMessage("تم إرجاع جميع كميات هذه الفاتورة");
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("اختر الصنف المرتجع")
                            .setItems(labels.toArray(new String[0]), (dialog, which) ->
                                    showReturnQuantity(invoice, returnable.get(which), saleReturn))
                            .setNegativeButton("إغلاق", null)
                            .show();
                });
            } catch (RuntimeException exception) {
                showError("تعذر قراءة أصناف الفاتورة", exception);
            }
        });
    }

    private void showReturnQuantity(Invoice invoice, InvoiceLine line, boolean saleReturn) {
        databaseExecutor.execute(() -> {
            try {
                InventoryItem item = database.inventoryDao().getItem(line.itemId);
                long returned = database.erpDao().getReturnedQuantity(line.lineId);
                long remaining = line.quantity - returned;
                runOnUiThread(() -> showReturnQuantity(
                        invoice, line, saleReturn, item, remaining));
            } catch (RuntimeException exception) {
                showError("تعذر قراءة الكمية المتبقية", exception);
            }
        });
    }

    private void showReturnQuantity(Invoice invoice, InvoiceLine line, boolean saleReturn,
                                   InventoryItem item, long remaining) {
        LinearLayout form = createForm();
        EditText quantity = addField(form, "الكمية (المتبقي " + remaining + ")", true);
        CheckBox restock = new CheckBox(this);
        restock.setText("إعادة الصنف الصالح إلى المخزون");
        restock.setChecked(true);
        if (saleReturn) {
            form.addView(restock);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((item == null ? "الصنف" : item.itemName) + " — مرتجع")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل المرتجع", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long count;
                    try {
                        count = parsePositive(quantity.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    if (count > remaining) {
                        showMessage("الكمية أكبر من المتبقي في الفاتورة الأصلية");
                        return;
                    }
                    boolean returnToStock = saleReturn && restock.isChecked();
                    String fingerprint = invoice.invoiceId + "|" + line.lineId + "|"
                            + count + "|" + returnToStock;
                    String operationKey;
                    try {
                        operationKey = pendingKey(saleReturn ? "sale-return" : "purchase-return",
                                fingerprint);
                    } catch (RuntimeException exception) {
                        showError("تعذر حفظ مفتاح منع التكرار", exception);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            ReturnService.ReturnLine request = new ReturnService.ReturnLine(
                                    line.lineId, count, returnToStock);
                            Invoice result = saleReturn
                                    ? new ReturnService(database).returnSale(invoice.invoiceId,
                                            Arrays.asList(request), operationKey)
                                    : new ReturnService(database).returnPurchase(invoice.invoiceId,
                                            Arrays.asList(request), operationKey);
                            boolean keyCleared = clearPendingKey(
                                    saleReturn ? "sale-return" : "purchase-return", operationKey);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage("رُحّل المرتجع " + result.invoiceId
                                        + " مع أثره المخزني والمالي"
                                        + pendingKeyWarning(keyCleared));
                            });
                        } catch (RuntimeException exception) {
                            showError("لم يُرحّل المرتجع", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private void showWithdrawalTypePicker() {
        new AlertDialog.Builder(this)
                .setTitle("مسحوبات المالك")
                .setItems(new String[]{"مسحوب نقدي", "مسحوب عيني من المخزون"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showCashWithdrawalForm();
                            } else {
                                showInventoryWithdrawalPicker();
                            }
                        })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showCashWithdrawalForm() {
        LinearLayout form = createForm();
        EditText amount = addField(form, "المبلغ (أصغر وحدة نقدية)", true);
        Spinner currency = spinner(form, MoneyCurrency.all());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مسحوب مالك نقدي")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long value;
                    try {
                        value = parsePositive(amount.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    String code = currency.getSelectedItem().toString();
                    String fingerprint = value + "|" + code;
                    String operationKey;
                    try {
                        operationKey = pendingKey("owner-withdrawal-cash", fingerprint);
                    } catch (RuntimeException exception) {
                        showError("تعذر حفظ مفتاح منع التكرار", exception);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            OwnerWithdrawal withdrawal = new OwnerWithdrawalService(database)
                                    .withdrawCash(value, code, operationKey);
                            boolean keyCleared = clearPendingKey(
                                    "owner-withdrawal-cash", operationKey);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage("رُحّلت مسحوبات المالك " + withdrawal.withdrawalId
                                        + pendingKeyWarning(keyCleared));
                            });
                        } catch (RuntimeException exception) {
                            showError("لم تُرحّل المسحوبات", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private void showInventoryWithdrawalPicker() {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.POST_WITHDRAWAL);
                List<InventoryItem> items = database.inventoryDao().getAllItems();
                runOnUiThread(() -> {
                    String[] labels = new String[items.size()];
                    for (int i = 0; i < items.size(); i++) {
                        InventoryItem item = items.get(i);
                        labels[i] = item.itemName + " — متوفر " + item.quantity;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("اختر الصنف")
                            .setItems(labels, (dialog, which) ->
                                    showInventoryWithdrawalQuantity(items.get(which)))
                            .setNegativeButton("إغلاق", null)
                            .show();
                });
            } catch (RuntimeException exception) {
                showError("تعذر قراءة المخزون", exception);
            }
        });
    }

    private void showInventoryWithdrawalQuantity(InventoryItem item) {
        EditText quantity = new EditText(this);
        quantity.setHint("الكمية المتاحة " + item.quantity);
        quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مسحوب عيني: " + item.itemName)
                .setView(quantity)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("ترحيل", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long count;
                    try {
                        count = parsePositive(quantity.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    String fingerprint = item.itemId + "|" + count;
                    String operationKey;
                    try {
                        operationKey = pendingKey("owner-withdrawal-kind", fingerprint);
                    } catch (RuntimeException exception) {
                        showError("تعذر حفظ مفتاح منع التكرار", exception);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            OwnerWithdrawal withdrawal = new OwnerWithdrawalService(database)
                                    .withdrawInventory(item.itemId, count, operationKey);
                            boolean keyCleared = clearPendingKey(
                                    "owner-withdrawal-kind", operationKey);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage("رُحّل المسحوب العيني " + withdrawal.withdrawalId
                                        + pendingKeyWarning(keyCleared));
                            });
                        } catch (RuntimeException exception) {
                            showError("لم يُرحّل المسحوب العيني", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private LinearLayout createForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, 0);
        return form;
    }

    private EditText addField(LinearLayout form, String hint, boolean numeric) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        form.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    private Spinner spinner(LinearLayout form, List<String> values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        form.addView(spinner);
        return spinner;
    }

    private static String[] partnerNames(List<BusinessPartner> partners) {
        String[] result = new String[partners.size()];
        for (int i = 0; i < partners.size(); i++) {
            result[i] = partners.get(i).name;
        }
        return result;
    }

    private long parsePositive(String raw) {
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("أدخل عددًا صحيحًا أكبر من صفر");
        }
    }

    private long parseNonNegative(String raw) {
        if (raw.trim().isEmpty()) {
            return 0;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("أدخل مبلغًا صحيحًا غير سالب");
        }
    }

    private String pendingKey(String operation, String fingerprint) {
        String prefName = "pending_accounting_" + operation;
        android.content.SharedPreferences prefs = getSharedPreferences(prefName, MODE_PRIVATE);
        String storedFingerprint = prefs.getString("fingerprint", null);
        String key = prefs.getString("key", null);
        if (fingerprint.equals(storedFingerprint) && key != null && !key.trim().isEmpty()) {
            return key;
        }
        key = UUID.randomUUID().toString();
        if (!prefs.edit().putString("fingerprint", fingerprint).putString("key", key).commit()) {
            throw new IllegalStateException("تعذر حفظ مفتاح منع التكرار");
        }
        return key;
    }

    private boolean clearPendingKey(String operation, String key) {
        android.content.SharedPreferences prefs = getSharedPreferences(
                "pending_accounting_" + operation, MODE_PRIVATE);
        return !key.equals(prefs.getString("key", null)) || prefs.edit().clear().commit();
    }

    private static String pendingKeyWarning(boolean cleared) {
        return cleared ? "" : "؛ حُفظت العملية، وتعذر تنظيف مفتاح منع التكرار. "
                + "أعد المحاولة بالمفتاح نفسه لتجنب التكرار";
    }

    private static String purchaseFingerprint(String supplierId, String currency,
                                              List<PurchaseService.PurchaseLine> lines,
                                              long directCosts, long paid) {
        StringBuilder value = new StringBuilder(supplierId).append('|').append(currency)
                .append('|').append(directCosts).append('|').append(paid);
        for (PurchaseService.PurchaseLine line : lines) {
            value.append('|').append(line.itemId).append(':')
                    .append(line.quantity).append(':').append(line.unitPrice);
        }
        return value.toString();
    }

    private void showError(String action, Exception exception) {
        String message = exception.getMessage();
        runOnUiThread(() -> showMessage(action + ": "
                + (message == null || message.trim().isEmpty() ? "تعذر تنفيذ العملية" : message)));
    }

    private void showMessage(String message) {
        if (!isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (database != null) {
            databaseExecutor.execute(database::close);
        }
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
