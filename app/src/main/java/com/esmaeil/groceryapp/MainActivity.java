package com.esmaeil.groceryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;

import android.provider.ContactsContract;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int MAX_BACKUP_BYTES = 20 * 1024 * 1024;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final List<InventoryItem> searchResults = new ArrayList<>();
    private final List<SaleService.SaleLine> saleCart = new ArrayList<>();
    private final List<String> saleCartLabels = new ArrayList<>();
    private AppDatabase database;
    private AutoCompleteTextView itemSearch;
    private EditText quantityInput;
    private TextView cashCollections;
    private TextView todaySales;
    private TextView saleCartSummary;
    private EditText partnerPhoneField;
    private EditText activeBarcodeField;
    private String saleCartCurrency;
    private ArrayAdapter<String> itemAdapter;
    private InventoryItem selectedItem;
    private ActivityResultLauncher<Intent> importFileLauncher;
    private ActivityResultLauncher<Intent> exportFileLauncher;
    private ActivityResultLauncher<Intent> importRoomBackupLauncher;
    private ActivityResultLauncher<Intent> barcodeScannerLauncher;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = AppDatabase.open(getApplicationContext());
        if (!SessionManager.hasSession()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        itemSearch = findViewById(R.id.etItemSearch);
        quantityInput = findViewById(R.id.etQuantity);
        saleCartSummary = findViewById(R.id.tvSaleCart);
        cashCollections = findViewById(R.id.tvCashBalance);
        todaySales = findViewById(R.id.tvTodaySales);

        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        importLegacyBackup(result.getData().getData());
                    }
                });
        barcodeScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        IntentResult scan = IntentIntegrator.parseActivityResult(
                                result.getResultCode(), result.getData());
                        String barcodeValue = scan == null ? null : scan.getContents();
                        if (barcodeValue != null && !barcodeValue.trim().isEmpty()) {
                            if (activeBarcodeField != null) {
                                activeBarcodeField.setText(barcodeValue.trim());
                            } else {
                                itemSearch.setText(barcodeValue.trim());
                                loadItemByBarcode(barcodeValue.trim());
                            }
                        }
                    }
                });
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    Uri contactUri = result.getData() == null ? null : result.getData().getData();
                    if (result.getResultCode() == RESULT_OK && contactUri != null
                            && partnerPhoneField != null) {
                        try (Cursor cursor = getContentResolver().query(contactUri,
                                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                                null, null, null)) {
                            if (cursor != null && cursor.moveToFirst()) {
                                partnerPhoneField.setText(cursor.getString(0));
                            }
                        } catch (RuntimeException exception) {
                            showDatabaseError("تعذر قراءة رقم جهة الاتصال", exception);
                        }
                    }
                    partnerPhoneField = null;
                });
        exportFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        exportLegacyBackup(result.getData().getData());
                    }
                });
        importRoomBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        requestRoomBackupRestore(result.getData().getData());
                    }
                });

        itemAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        itemSearch.setAdapter(itemAdapter);
        itemSearch.setThreshold(1);
        itemSearch.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < searchResults.size()) {
                selectedItem = searchResults.get(position);
            }
        });
        itemSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                selectedItem = null;
                loadItemSuggestions(value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {}
        });

        findViewById(R.id.btnExecuteSale).setOnClickListener(view -> submitCashSale());
        findViewById(R.id.btnCreditSale).setOnClickListener(view -> askCustomerForCreditSale());
        findViewById(R.id.btnAddSaleLine).setOnClickListener(view -> addSelectedItemToSaleCart());
        findViewById(R.id.btnManageInventory).setOnClickListener(
                view -> showInventoryManagement());
        findViewById(R.id.btnManagePartners).setOnClickListener(
                view -> showPartnerTypePicker());
        findViewById(R.id.btnAccountingOperations).setOnClickListener(view ->
                startActivity(new Intent(this, AccountingOperationsActivity.class)));
        findViewById(R.id.btnManageUsers).setOnClickListener(view -> showCreateUserDialog());
        findViewById(R.id.btnPrintStatement).setOnClickListener(view -> showSalesHistory());
        findViewById(R.id.btnShareReport).setOnClickListener(view -> requestLegacyExport());
        findViewById(R.id.btnImportLegacy).setOnClickListener(view -> requestLegacyImport());
        findViewById(R.id.btnRoomBackup).setOnClickListener(view -> showRoomBackupMenu());
        findViewById(R.id.btnAbout).setOnClickListener(
                view -> startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(view -> {
            SessionManager.clear();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        refreshDashboard();
        loadItemSuggestions("");
    }

    private void loadItemSuggestions(String query) {
        databaseExecutor.execute(() -> {
            try {
                List<InventoryItem> items = database.inventoryDao().searchItems(query.trim());
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    searchResults.clear();
                    searchResults.addAll(items);
                    itemAdapter.clear();
                    for (InventoryItem item : items) {
                        itemAdapter.add(item.itemName + " (المتوفر: " + item.quantity
                                    + "، السعر: " + item.sellingPrice + " " + item.currencyCode + ")");
                    }
                    itemAdapter.notifyDataSetChanged();
                });
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر قراءة الأصناف من Room", exception);
            }
        });
    }

    private void submitCashSale() {
        submitSale(0, null, true);
    }

    private void askCustomerForCreditSale() {
        LinearLayout form = createForm();
        EditText customer = addField(form, "اسم العميل (مطلوب عند وجود متبقي)", "", false);
        EditText paid = addField(form, "المبلغ المدفوع الآن (0 للبيع الآجل)", "0", true);
        new AlertDialog.Builder(this)
                .setTitle("بيع آجل أو جزئي")
                .setMessage("سيسجل المتبقي ذمةً على العميل.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("اعتماد البيع", (dialog, which) -> {
                    try {
                        long amount = parseNonNegative(paid.getText().toString());
                        submitSale(amount, customer.getText().toString(), false);
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                    }
                })
                .show();
    }

    private List<SaleService.SaleLine> currentSaleLines() {
        if (!saleCart.isEmpty()) {
            return new ArrayList<>(saleCart);
        }
        if (selectedItem == null) {
            showMessage("اختر صنفًا أو أضفه إلى الفاتورة أولًا");
            return null;
        }
        try {
            long quantity = parsePositive(quantityInput.getText().toString());
            return java.util.Collections.singletonList(
                    new SaleService.SaleLine(selectedItem.itemId, quantity));
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
            return null;
        }
    }

    private void addSelectedItemToSaleCart() {
        if (selectedItem == null) {
            showMessage("اختر صنفًا من نتائج البحث أولًا");
            return;
        }
        long quantity;
        try {
            quantity = parsePositive(quantityInput.getText().toString());
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
            return;
        }
        if (saleCartCurrency != null
                && !saleCartCurrency.equals(selectedItem.currencyCode)) {
            showMessage("لا يمكن جمع عملتين مختلفتين في فاتورة واحدة");
            return;
        }
        for (int i = 0; i < saleCart.size(); i++) {
            SaleService.SaleLine line = saleCart.get(i);
            if (line.itemId.equals(selectedItem.itemId)) {
                saleCart.set(i, new SaleService.SaleLine(line.itemId,
                        FinancialMath.add(line.quantity, quantity)));
                saleCartLabels.set(i, selectedItem.itemName + " × "
                        + (line.quantity + quantity));
                refreshSaleCartSummary();
                clearSaleLineInput();
                return;
            }
        }
        saleCart.add(new SaleService.SaleLine(selectedItem.itemId, quantity));
        saleCartLabels.add(selectedItem.itemName + " × " + quantity);
        saleCartCurrency = selectedItem.currencyCode;
        refreshSaleCartSummary();
        clearSaleLineInput();
    }

    private void clearSaleLineInput() {
        quantityInput.setText("");
        itemSearch.setText("");
    }

    private void refreshSaleCartSummary() {
        if (saleCartSummary == null) {
            return;
        }
        if (saleCart.isEmpty()) {
            saleCartSummary.setText("لم تُضف أصناف إلى الفاتورة");
            return;
        }
        StringBuilder text = new StringBuilder("أصناف الفاتورة:\n");
        for (String line : saleCartLabels) {
            text.append("• ").append(line).append('\n');
        }
        saleCartSummary.setText(text.toString());
    }

    private void submitSale(long amountPaid, String customerName, boolean payFullAmount) {
        List<SaleService.SaleLine> lines = currentSaleLines();
        if (lines == null) {
            return;
        }
        Button cashButton = findViewById(R.id.btnExecuteSale);
        Button creditButton = findViewById(R.id.btnCreditSale);
        Button addLineButton = findViewById(R.id.btnAddSaleLine);
        cashButton.setEnabled(false);
        creditButton.setEnabled(false);
        addLineButton.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                InventoryItem firstItem = database.inventoryDao().getItem(lines.get(0).itemId);
                if (firstItem == null) {
                    throw new IllegalArgumentException("الصنف غير موجود");
                }
                String saleCurrency = firstItem.currencyCode;
                StringBuilder fingerprintBuilder = new StringBuilder(saleCurrency).append('|')
                        .append(payFullAmount ? "FULL" : amountPaid).append('|')
                        .append(customerName == null ? "" : customerName.trim()
                                .toLowerCase(Locale.ROOT));
                for (SaleService.SaleLine line : lines) {
                    fingerprintBuilder.append('|').append(line.itemId)
                            .append(':').append(line.quantity);
                }
                String fingerprint = fingerprintBuilder.toString();
                SharedPreferences pendingSale = getSharedPreferences(
                        "pending_sale", MODE_PRIVATE);
                String storedFingerprint = pendingSale.getString("fingerprint", null);
                String operationKey = fingerprint.equals(storedFingerprint)
                        ? pendingSale.getString("operation_key", null) : null;
                if (operationKey == null || operationKey.trim().isEmpty()) {
                    operationKey = UUID.randomUUID().toString();
                    boolean saved = pendingSale.edit()
                            .putString("fingerprint", fingerprint)
                            .putString("operation_key", operationKey)
                            .commit();
                    if (!saved) {
                        throw new IllegalStateException("تعذر حفظ مفتاح إعادة المحاولة؛ لم يُرحّل البيع");
                    }
                }
                Invoice invoice = payFullAmount
                        ? new SaleService(database).postSaleFullyPaid(
                                lines, saleCurrency, operationKey)
                        : new SaleService(database).postSale(
                                lines, amountPaid, customerName, saleCurrency, operationKey);
                boolean pendingCleared = pendingSale.edit().clear().commit();
                String resultMessage = "تم حفظ الفاتورة " + invoice.invoiceId;
                if (!pendingCleared) {
                    resultMessage += "؛ احتُفظ بمفتاح إعادة المحاولة لتجنب التكرار";
                }
                String finalResultMessage = resultMessage;
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        showMessage(finalResultMessage);
                        saleCart.clear();
                        saleCartLabels.clear();
                        saleCartCurrency = null;
                        refreshSaleCartSummary();
                        quantityInput.setText("");
                        itemSearch.setText("");
                        refreshDashboard();
                        loadItemSuggestions("");
                    }
                });
            } catch (RuntimeException exception) {
                showDatabaseError("لم يُحفظ البيع", exception);
            } finally {
                runOnUiThread(() -> {
                    cashButton.setEnabled(true);
                    creditButton.setEnabled(true);
                    addLineButton.setEnabled(true);
                });
            }
        });
    }

    private void refreshDashboard() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long dayStart = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        long dayEnd = calendar.getTimeInMillis();

        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database,
                        SessionManager.Permission.VIEW_FINANCIAL_REPORTS);
                long salesYer = database.erpDao().getSalesTotal(
                        dayStart, dayEnd, MoneyCurrency.YER);
                long salesSar = database.erpDao().getSalesTotal(
                        dayStart, dayEnd, MoneyCurrency.SAR);
                long collectionsYer = database.erpDao().getCashBalance(MoneyCurrency.YER);
                long collectionsSar = database.erpDao().getCashBalance(MoneyCurrency.SAR);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        todaySales.setText("مبيعات اليوم — YER: " + salesYer
                                + "، SAR: " + salesSar);
                        cashCollections.setText("رصيد الصندوق — YER: " + collectionsYer
                                + "، SAR: " + collectionsSar);
                    }
                });
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر تحميل مؤشرات Room", exception);
            }
        });
    }

    private void showInventoryManagement() {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database,
                        SessionManager.Permission.MANAGE_INVENTORY);
                List<InventoryItem> items = database.inventoryDao().getAllItems();
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    String[] labels = new String[items.size()];
                    for (int i = 0; i < items.size(); i++) {
                        InventoryItem item = items.get(i);
                        labels[i] = item.itemName + " — مخزون: " + item.quantity
                                + " — بيع: " + item.sellingPrice + " " + item.currencyCode;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("الأصناف النشطة")
                            .setItems(labels, (dialog, which) -> showItemActions(items.get(which)))
                            .setPositiveButton("إضافة صنف", (dialog, which) -> showItemEditor(null))
                            .setNegativeButton("إغلاق", null)
                            .show();
                });
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر قراءة المخزون", exception);
            }
        });
    }

    private void showItemActions(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle(item.itemName)
                .setItems(new String[]{"تعديل البيانات", "تسجيل رصيد افتتاحي", "إلغاء تنشيط"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showItemEditor(item);
                            } else if (which == 1) {
                                showOpeningStockDialog(item);
                            } else {
                                new AlertDialog.Builder(this)
                                        .setMessage("سيُخفى الصنف دون حذف سجلاته أو فواتيره.")
                                        .setNegativeButton("إلغاء", null)
                                        .setPositiveButton("إلغاء التنشيط", (confirm, selected) ->
                                                databaseExecutor.execute(() -> {
                                                    try {
                                                        SessionManager.require(database,
                                                                SessionManager.Permission.MANAGE_INVENTORY);
                                                        if (database.inventoryDao()
                                                                .softDeleteItem(item.itemId) != 1) {
                                                            throw new IllegalStateException(
                                                                    "الصنف غير نشط بالفعل");
                                                        }
                                                        runOnUiThread(() -> {
                                                            showMessage("تم إلغاء تنشيط الصنف");
                                                            showInventoryManagement();
                                                            loadItemSuggestions("");
                                                        });
                                                    } catch (RuntimeException exception) {
                                                        showDatabaseError(
                                                                "تعذر إلغاء تنشيط الصنف", exception);
                                                    }
                                                }))
                                        .show();
                            }
                        })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showItemEditor(InventoryItem existing) {
        LinearLayout form = createForm();
        EditText name = addField(form, "اسم الصنف", existing == null ? "" : existing.itemName, false);
        EditText barcode = addField(form, "الباركود (اختياري)", existing == null
                ? "" : existing.barcode, false);
        activeBarcodeField = barcode;
        Button scanBarcode = new Button(this);
        scanBarcode.setText("مسح الباركود بالكاميرا");
        scanBarcode.setOnClickListener(view -> openBarcodeScanner());
        form.addView(scanBarcode);
        EditText quantity = addField(form, existing == null
                ? "كمية الرصيد الافتتاحي (0 بلا رصيد)" : "الكمية الحالية",
                existing == null ? "0" : Long.toString(existing.quantity), true);
        EditText unitCost = addField(form, existing == null
                ? "تكلفة الوحدة الافتتاحية (0 بلا رصيد)" : "متوسط تكلفة الوحدة",
                existing == null ? "0" : Long.toString(existing.purchasePriceAverage), true);
        if (existing != null) {
            quantity.setEnabled(false);
            unitCost.setEnabled(false);
        }
        EditText sellingPrice = addField(form, "سعر البيع", existing == null
                ? "" : Long.toString(existing.sellingPrice), true);
        Spinner currency = new Spinner(this);
        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MoneyCurrency.all());
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currency.setAdapter(currencyAdapter);
        if (existing != null && MoneyCurrency.SAR.equals(existing.currencyCode)) {
            currency.setSelection(1);
        }
        form.addView(currency);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "إضافة صنف" : "تعديل بيانات الصنف")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create();
        dialog.setOnDismissListener(ignored -> activeBarcodeField = null);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String itemName = name.getText().toString().trim();
                    String rawPrice = sellingPrice.getText().toString().trim();
                    long price;
                    long initialQuantity;
                    long initialCost;
                    try {
                        price = Long.parseLong(rawPrice);
                        initialQuantity = parseNonNegative(quantity.getText().toString());
                        initialCost = parseNonNegative(unitCost.getText().toString());
                    } catch (NumberFormatException exception) {
                        showMessage("أدخل سعرًا وكمية وتكلفة صحيحة");
                        return;
                    } catch (IllegalArgumentException exception) {
                        showMessage(exception.getMessage());
                        return;
                    }
                    if (itemName.isEmpty() || price <= 0) {
                        showMessage("اسم الصنف وسعر البيع الموجب مطلوبان");
                        return;
                    }
                    if ((initialQuantity == 0) != (initialCost == 0)) {
                        showMessage("أدخل كمية وتكلفة افتتاحيتين موجبتين معًا، أو اتركهما صفرًا");
                        return;
                    }
                    InventoryItem item = existing == null ? new InventoryItem() : existing;
                    if (existing == null) {
                        item.itemId = UUID.randomUUID().toString();
                        item.quantity = 0;
                        item.purchasePriceAverage = 0;
                        item.minStockAlert = 0;
                        item.category = "عام";
                    }
                    item.itemName = itemName;
                    item.barcode = barcode.getText().toString().trim();
                    if (item.barcode.isEmpty()) {
                        item.barcode = null;
                    }
                    item.sellingPrice = price;
                    item.currencyCode = currency.getSelectedItem().toString();
                    databaseExecutor.execute(() -> {
                        try {
                            SessionManager.require(database,
                                    SessionManager.Permission.MANAGE_INVENTORY);
                            if (existing == null) {
                                String fingerprint = item.itemName + "|" + item.barcode + "|"
                                        + initialQuantity + "|" + initialCost + "|" + price
                                        + "|" + item.currencyCode;
                                String operationKey = pendingKey("inventory-item", fingerprint);
                                new InventoryService(database).createItem(
                                        item, initialQuantity, initialCost, operationKey);
                                boolean keyCleared = clearPendingKey("inventory-item");
                                runOnUiThread(() -> {
                                    dialog.dismiss();
                                    showMessage(keyCleared ? "تم حفظ الصنف ورصيده في Room"
                                            : "تم الحفظ؛ احتُفظ بمفتاح منع التكرار لإعادة المحاولة");
                                    refreshDashboard();
                                    loadItemSuggestions("");
                                });
                            } else if (database.inventoryDao().updateItem(item) != 1) {
                                throw new IllegalStateException("لم يعد الصنف موجودًا");
                            } else {
                                runOnUiThread(() -> {
                                    dialog.dismiss();
                                    showMessage("تم حفظ بيانات الصنف في Room");
                                    refreshDashboard();
                                    loadItemSuggestions("");
                                });
                            }
                        } catch (RuntimeException exception) {
                            showDatabaseError("تعذر حفظ الصنف", exception);
                        }
                    });
                }));
        dialog.show();
    }

    private void showOpeningStockDialog(InventoryItem item) {
        LinearLayout form = createForm();
        EditText quantity = addField(form, "كمية الرصيد الافتتاحي", "", true);
        EditText unitCost = addField(form, "تكلفة الوحدة", "", true);
        new AlertDialog.Builder(this)
                .setTitle("إثبات رصيد مخزون افتتاحي")
                .setMessage("سيُسجل قيدًا افتتاحيًا متوازنًا في الأستاذ.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("اعتماد", (dialog, which) -> {
                    try {
                        long count = Long.parseLong(quantity.getText().toString().trim());
                        long cost = Long.parseLong(unitCost.getText().toString().trim());
                        String operationKey = UUID.randomUUID().toString();
                        databaseExecutor.execute(() -> {
                            try {
                                new InventoryService(database).postOpeningStock(
                                        item.itemId, count, cost, operationKey);
                                runOnUiThread(() -> {
                                    showMessage("تم حفظ حركة الرصيد الافتتاحي");
                                    refreshDashboard();
                                    loadItemSuggestions("");
                                    showInventoryManagement();
                                });
                            } catch (RuntimeException exception) {
                                showDatabaseError("لم يُحفظ رصيد المخزون", exception);
                            }
                        });
                    } catch (NumberFormatException exception) {
                        showMessage("أدخل كمية وتكلفة صحيحتين");
                    }
                })
                .show();
    }

    private void showPartnerTypePicker() {
        new AlertDialog.Builder(this)
                .setTitle("إدارة الشركاء")
                .setItems(new String[]{"العملاء", "الموردون"}, (dialog, which) ->
                        showPartners(which == 0 ? BusinessPartner.TYPE_CUSTOMER
                                : BusinessPartner.TYPE_SUPPLIER))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showCreateUserDialog() {
        LinearLayout form = createForm();
        EditText username = addField(form, "اسم المستخدم", "", false);
        EditText pin = addField(form, "PIN (6 إلى 12 رقمًا)", "", true);
        Spinner role = new Spinner(this);
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"مدير", "كاشير"});
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        role.setAdapter(roleAdapter);
        form.addView(role);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إنشاء مستخدم موظف")
                .setMessage("المالك فقط يستطيع إنشاء حساب مدير أو كاشير.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إنشاء", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String staffUsername = username.getText().toString();
                    char[] staffPin = pin.getText().toString().toCharArray();
                    String staffRole = role.getSelectedItemPosition() == 0
                            ? AppUser.ROLE_MANAGER : AppUser.ROLE_CASHIER;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            String recoveryCode = new AuthService(database)
                                    .createStaffUser(staffUsername, staffPin, staffRole);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                new AlertDialog.Builder(this)
                                        .setTitle("تم إنشاء المستخدم")
                                        .setMessage("سلّم المستخدم رمز الاستعادة واحفظه بأمان؛ "
                                                + "لن يُعرض مرة أخرى:\n\n" + recoveryCode)
                                        .setPositiveButton("موافق", null)
                                        .show();
                            });
                        } catch (RuntimeException exception) {
                            showDatabaseError("تعذر إنشاء المستخدم", exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        } finally {
                            java.util.Arrays.fill(staffPin, '\0');
                        }
                    });
                }));
        dialog.show();
    }

    private void showPartners(String type) {
        EditText search = new EditText(this);
        search.setHint("البحث بالاسم");
        new AlertDialog.Builder(this)
                .setTitle(type.equals(BusinessPartner.TYPE_CUSTOMER) ? "بحث العملاء" : "بحث الموردين")
                .setView(search)
                .setNegativeButton("إغلاق", null)
                .setNeutralButton("عرض الكل", (dialog, which) -> loadPartners(type, ""))
                .setPositiveButton("بحث", (dialog, which) ->
                        loadPartners(type, search.getText().toString().trim()))
                .show();
    }

    private void loadPartners(String type, String query) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_PARTNERS);
                List<BusinessPartner> partners;
                if (query.isEmpty()) {
                    partners = type.equals(BusinessPartner.TYPE_CUSTOMER)
                            ? database.erpDao().getCustomers() : database.erpDao().getSuppliers();
                } else {
                    partners = type.equals(BusinessPartner.TYPE_CUSTOMER)
                            ? database.erpDao().searchCustomers(query)
                            : database.erpDao().searchSuppliers(query);
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    String[] names = new String[partners.size()];
                    for (int i = 0; i < partners.size(); i++) {
                        names[i] = partners.get(i).name;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(type.equals(BusinessPartner.TYPE_CUSTOMER)
                                    ? "العملاء" : "الموردون")
                            .setItems(names, (dialog, which) ->
                                    showPartnerActions(partners.get(which)))
                            .setPositiveButton("إضافة", (dialog, which) ->
                                    showPartnerEditor(type, null))
                            .setNegativeButton("إغلاق", null)
                            .show();
                });
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر قراءة الشركاء", exception);
            }
        });
    }

    private void showPartnerActions(BusinessPartner partner) {
        new AlertDialog.Builder(this)
                .setTitle(partner.name)
                .setItems(new String[]{"تعديل البيانات", "إلغاء التنشيط"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showPartnerEditor(partner.type, partner);
                            } else {
                                new AlertDialog.Builder(this)
                                        .setMessage("سيُخفى الشريك مع الاحتفاظ بالفواتير.")
                                        .setNegativeButton("إلغاء", null)
                                        .setPositiveButton("إلغاء التنشيط", (confirm, selected) ->
                                                databaseExecutor.execute(() -> {
                                                        try {
                                                            SessionManager.require(database,
                                                                    SessionManager.Permission.MANAGE_PARTNERS);
                                                            if (database.erpDao()
                                                                    .softDeletePartner(partner.partnerId) != 1) {
                                                                throw new IllegalStateException(
                                                                        "الشريك غير نشط بالفعل");
                                                            }
                                                            runOnUiThread(() -> {
                                                                showMessage("تم إلغاء تنشيط الشريك");
                                                                showPartners(partner.type);
                                                            });
                                                        } catch (RuntimeException exception) {
                                                            showDatabaseError(
                                                                    "تعذر إلغاء تنشيط الشريك", exception);
                                                        }
                                                }))
                                        .show();
                            }
                        })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showPartnerEditor(String type, BusinessPartner existing) {
        LinearLayout form = createForm();
        EditText name = addField(form, "الاسم", existing == null ? "" : existing.name, false);
        EditText phone = addField(form, "الهاتف", existing == null ? "" : existing.phone, false);
        partnerPhoneField = phone;
        Button choosePhone = new Button(this);
        choosePhone.setText("اختيار رقم من جهات الاتصال");
        choosePhone.setOnClickListener(view -> chooseContactPhone(phone));
        form.addView(choosePhone);
        EditText address = addField(form, "العنوان", existing == null ? "" : existing.address, false);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null
                        ? (BusinessPartner.TYPE_CUSTOMER.equals(type)
                        ? "إضافة عميل" : "إضافة مورد")
                        : (BusinessPartner.TYPE_CUSTOMER.equals(type)
                        ? "تعديل بيانات العميل" : "تعديل بيانات المورد"))
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (partnerPhoneField == phone) {
                partnerPhoneField = null;
            }
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String partnerName = name.getText().toString().trim();
                    if (partnerName.isEmpty()) {
                        showMessage("اسم الشريك مطلوب");
                        return;
                    }
                    BusinessPartner partner = existing == null ? new BusinessPartner() : existing;
                    if (existing == null) {
                        partner.partnerId = UUID.randomUUID().toString();
                        partner.type = type;
                        partner.createdAt = System.currentTimeMillis();
                    }
                    partner.name = partnerName;
                    partner.phone = phone.getText().toString().trim();
                    partner.address = address.getText().toString().trim();
                    databaseExecutor.execute(() -> {
                        try {
                            SessionManager.require(database,
                                    SessionManager.Permission.MANAGE_PARTNERS);
                            if (existing == null) {
                                database.erpDao().insertPartner(partner);
                            } else if (database.erpDao().updatePartner(partner) != 1) {
                                throw new IllegalStateException("لم يعد الشريك موجودًا");
                            }
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showMessage("تم حفظ الشريك في Room");
                            });
                        } catch (RuntimeException exception) {
                            showDatabaseError("تعذر حفظ الشريك", exception);
                        }
                    });
                }));
        dialog.show();
    }

    private void chooseContactPhone(EditText phoneField) {
        partnerPhoneField = phoneField;
        Intent pickPhone = new Intent(Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            contactPickerLauncher.launch(pickPhone);
        } catch (RuntimeException exception) {
            partnerPhoneField = null;
            showDatabaseError("تعذر فتح دليل جهات الاتصال", exception);
        }
    }

    private void openBarcodeScanner() {
        IntentIntegrator scanner = new IntentIntegrator(this);
        scanner.setDesiredBarcodeFormats(IntentIntegrator.ONE_D_CODE_TYPES);
        scanner.setPrompt("وجّه الكاميرا نحو باركود الصنف");
        scanner.setBeepEnabled(false);
        scanner.setOrientationLocked(true);
        try {
            barcodeScannerLauncher.launch(scanner.createScanIntent());
        } catch (RuntimeException exception) {
            showDatabaseError("تعذر تشغيل ماسح الباركود", exception);
        }
    }

    private void loadItemByBarcode(String barcodeValue) {
        databaseExecutor.execute(() -> {
            try {
                InventoryItem item = database.inventoryDao().getItemByBarcode(barcodeValue);
                runOnUiThread(() -> {
                    if (item == null) {
                        selectedItem = null;
                        showMessage("لا يوجد صنف نشط بهذا الباركود");
                    } else {
                        itemSearch.setText(item.itemName);
                        selectedItem = item;
                        itemSearch.dismissDropDown();
                    }
                });
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر البحث عن الباركود في Room", exception);
            }
        });
    }

    private void showSalesHistory() {
        EditText search = new EditText(this);
        search.setHint("رقم الفاتورة أو اسم العميل");
        new AlertDialog.Builder(this)
                .setTitle("البحث عن الفواتير")
                .setView(search)
                .setNegativeButton("إغلاق", null)
                .setNeutralButton("آخر 100", (dialog, which) -> loadSalesHistory(null))
                .setPositiveButton("بحث", (dialog, which) ->
                        loadSalesHistory(search.getText().toString().trim()))
                .show();
    }

    private void loadSalesHistory(String query) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database,
                        SessionManager.Permission.VIEW_FINANCIAL_REPORTS);
                List<Invoice> invoices = query == null ? database.erpDao().getRecentSales()
                        : database.erpDao().searchSales(query);
                runOnUiThread(() -> showInvoiceList(invoices));
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر البحث عن الفواتير", exception);
            }
        });
    }

    private void showInvoiceList(List<Invoice> invoices) {
        if (isFinishing()) {
            return;
        }
        if (invoices.isEmpty()) {
            showMessage("لا توجد فواتير مطابقة");
            return;
        }
        String[] rows = new String[invoices.size()];
        for (int i = 0; i < invoices.size(); i++) {
            Invoice invoice = invoices.get(i);
            rows[i] = invoice.type + " — " + invoice.total + " " + invoice.currencyCode
                    + " — " + invoice.status;
        }
        new AlertDialog.Builder(this)
                .setTitle("الفواتير المرحلة")
                .setItems(rows, (dialog, which) -> showInvoiceDetails(invoices.get(which)))
                .setPositiveButton("إغلاق", null)
                .show();
    }

    private void showInvoiceDetails(Invoice invoice) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database,
                        SessionManager.Permission.VIEW_FINANCIAL_REPORTS);
                List<InvoiceLine> lines = database.erpDao().getInvoiceLines(invoice.invoiceId);
                StringBuilder details = new StringBuilder()
                        .append("رقم الفاتورة: ").append(invoice.invoiceId)
                        .append("\nالحالة: ").append(invoice.status)
                        .append("\nالإجمالي: ").append(invoice.total).append(' ')
                        .append(invoice.currencyCode)
                        .append("\nالمدفوع: ").append(invoice.paid).append('\n');
                for (InvoiceLine line : lines) {
                    InventoryItem item = database.inventoryDao().getItem(line.itemId);
                    details.append("\n").append(item == null ? line.itemId : item.itemName)
                            .append(" × ").append(line.quantity)
                            .append(" — ").append(line.lineTotal);
                }
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        new AlertDialog.Builder(this)
                                .setTitle("تفاصيل الفاتورة")
                                .setMessage(details.toString())
                                .setPositiveButton("إغلاق", null)
                                .show();
                    }
                });
            } catch (RuntimeException exception) {
                showDatabaseError("تعذر قراءة تفاصيل الفاتورة", exception);
            }
        });
    }

    private void showRoomBackupMenu() {
        new AlertDialog.Builder(this)
                .setTitle("نسخة Room مشفرة")
                .setItems(new String[]{"إنشاء نسخة مشفرة على الجهاز",
                                "استعادة نسخة من ملف"},
                        (dialog, which) -> {
                            if (which == 0) {
                                requestRoomBackupPassword();
                            } else {
                                requestRoomBackupImport();
                            }
                        })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void requestRoomBackupImport() {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_BACKUPS);
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    importRoomBackupLauncher.launch(intent);
                });
            } catch (RuntimeException exception) {
                showDatabaseError("لا تملك صلاحية الاستعادة", exception);
            }
        });
    }

    private void requestRoomBackupPassword() {
        LinearLayout form = createForm();
        EditText password = addField(form, "كلمة مرور قوية (12 حرفًا على الأقل)", "", false);
        EditText confirm = addField(form, "تأكيد كلمة المرور", "", false);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("تشفير نسخة Room")
                .setMessage("احفظ كلمة المرور خارج الهاتف. لا يمكن استعادة الأرشيف دونها.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إنشاء النسخة", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    char[] first = password.getText().toString().toCharArray();
                    char[] second = confirm.getText().toString().toCharArray();
                    if (first.length < 12 || !java.util.Arrays.equals(first, second)) {
                        java.util.Arrays.fill(first, '\0');
                        java.util.Arrays.fill(second, '\0');
                        showMessage(first.length < 12
                                ? "كلمة المرور يجب ألا تقل عن 12 حرفًا"
                                : "كلمتا المرور غير متطابقتين");
                        return;
                    }
                    dialog.dismiss();
                    password.setText("");
                    confirm.setText("");
                    java.util.Arrays.fill(second, '\0');
                    createRoomBackup(first);
                }));
        dialog.show();
    }

    private void createRoomBackup(char[] password) {
        databaseExecutor.execute(() -> {
            RoomBackupManager.BackupResult backup = null;
            Exception failure = null;
            Exception reopenFailure = null;
            java.io.File backupFile = null;
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_BACKUPS);
                java.io.File directory = localRoomBackupDirectory();
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IOException("تعذر إنشاء مجلد النسخ المحلي");
                }
                backupFile = new java.io.File(directory,
                        "mohasabat_room_" + System.currentTimeMillis() + ".mbak");
                try (FileOutputStream output = new FileOutputStream(backupFile)) {
                    backup = RoomBackupManager.createEncryptedBackup(
                            getApplicationContext(), database, output, password);
                }
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            } finally {
                java.util.Arrays.fill(password, '\0');
                if (failure != null && backupFile != null && backupFile.exists()
                        && !backupFile.delete()) {
                    failure.addSuppressed(new IOException("تعذر حذف ملف النسخة غير المكتملة"));
                }
                if (!database.isOpen()) {
                    try {
                        database = AppDatabase.open(getApplicationContext());
                    } catch (RuntimeException exception) {
                        reopenFailure = exception;
                    }
                }
            }
            RoomBackupManager.BackupResult completedBackup = backup;
            Exception operationFailure = failure;
            Exception databaseFailure = reopenFailure;
            java.io.File savedBackupFile = backupFile;
            runOnUiThread(() -> {
                if (operationFailure != null) {
                    showDatabaseError("تعذر إنشاء النسخة المشفرة", operationFailure);
                    if (databaseFailure != null) {
                        showDatabaseError("تعذر إعادة فتح Room بعد النسخ", databaseFailure);
                    }
                } else {
                    String message = "اكتملت النسخة المشفرة. SHA-256 للأرشيف:\n"
                            + completedBackup.archiveSha256
                            + "\n\nالمسار المحلي: " + savedBackupFile.getAbsolutePath()
                            + "\nلم تُرسل النسخة إلى خدمة سحابية.";
                    if (databaseFailure != null) {
                        message += "\nتعذر إعادة فتح قاعدة التطبيق؛ أعد تشغيل التطبيق "
                                + "ولا تحذف النسخة أو القاعدة الأصلية.";
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("اكتمل النسخ المحلي")
                            .setMessage(message)
                            .setPositiveButton("موافق", null)
                            .show();
                }
            });
        });
    }

    private java.io.File localRoomBackupDirectory() {
        java.io.File documents = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        java.io.File base = documents == null ? getFilesDir() : documents;
        return new java.io.File(base, "room-backups");
    }

    private void requestRoomBackupRestore(Uri uri) {
        LinearLayout form = createForm();
        EditText password = addField(form, "كلمة مرور النسخة", "", false);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("فك تشفير النسخة")
                .setMessage("يُفحص الأرشيف في قاعدة مؤقتة أولًا؛ لن تُستبدل البيانات الآن.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("فحص", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    char[] passphrase = password.getText().toString().toCharArray();
                    if (passphrase.length < 12) {
                        java.util.Arrays.fill(passphrase, '\0');
                        showMessage("كلمة المرور يجب ألا تقل عن 12 حرفًا");
                        return;
                    }
                    dialog.dismiss();
                    password.setText("");
                    inspectRoomBackup(uri, passphrase);
                }));
        dialog.show();
    }

    private void inspectRoomBackup(Uri uri, char[] password) {
        databaseExecutor.execute(() -> {
            RoomBackupManager.RestoreCandidate candidate = null;
            try {
                InputStream input = getContentResolver().openInputStream(uri);
                if (input == null) {
                    throw new IOException("تعذر قراءة ملف النسخة");
                }
                candidate = RoomBackupManager.inspectEncryptedBackup(
                        getApplicationContext(), input, password);
            } catch (IOException | RuntimeException exception) {
                java.util.Arrays.fill(password, '\0');
                showDatabaseError("فشل فحص النسخة؛ لم تتغير قاعدة التطبيق", exception);
                return;
            }
            java.util.Arrays.fill(password, '\0');
            RoomBackupManager.RestoreCandidate inspected = candidate;
            databaseExecutor.execute(() -> {
                try {
                    long currentLatest = latestLocalDatabaseChangeTime();
                    long currentInvoices = countCurrentRows("invoices");
                    long currentItems = countCurrentRows("inventory_items");
                    long currentJournal = countCurrentRows("journal_entries");
                    runOnUiThread(() -> showRestorePreview(
                            inspected, currentLatest, currentInvoices, currentItems,
                            currentJournal));
                } catch (RuntimeException exception) {
                    inspected.close();
                    showDatabaseError("تعذر مقارنة النسخة ببيانات الجهاز", exception);
                }
            });
        });
    }

    private void showRestorePreview(RoomBackupManager.RestoreCandidate candidate,
                                    long currentLatest, long currentInvoices,
                                    long currentItems, long currentJournal) {
        if (isFinishing()) {
            candidate.close();
            return;
        }
        java.text.DateFormat dateFormat = java.text.DateFormat.getDateTimeInstance();
        boolean older = candidate.createdAt < currentLatest;
        String details = "تاريخ النسخة: " + dateFormat.format(
                new java.util.Date(candidate.createdAt))
                + "\nإصدار التطبيق: " + candidate.appVersion
                + "\nإصدار Room: " + candidate.databaseVersion
                + "\nالفواتير: " + candidate.tableCounts.get("invoices")
                + "، الأصناف: " + candidate.tableCounts.get("inventory_items")
                + "، القيود: " + candidate.tableCounts.get("journal_entries")
                + "، سندات الدفع: " + candidate.tableCounts.get("payments")
                + "\nالبيانات الحالية على الجهاز — الفواتير: " + currentInvoices
                + "، الأصناف: " + currentItems + "، القيود: " + currentJournal
                + (older ? "\n\nتحذير: تاريخ النسخة أقدم من آخر تعديل محلي مرصود؛ "
                        + "قد تحتوي النسخة الحالية على بيانات أحدث." : "")
                + "\n\nستُحفظ قاعدة الجهاز الحالية بملف رجوع ولن تُحذف. "
                + "لن تُعتمد الاستعادة إلا بعد موافقتك الصريحة.";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("معاينة نسخة صالحة")
                .setMessage(details)
                .setNegativeButton("إلغاء", (ignored, which) -> candidate.close())
                .setPositiveButton("استعادة بعد الموافقة", null)
                .create();
        dialog.setOnCancelListener(ignored -> candidate.close());
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            RoomBackupManager.RestoreResult result = RoomBackupManager.restore(
                                    getApplicationContext(), database, candidate);
                            database = result.database;
                            SessionManager.clear();
                            runOnUiThread(() -> new AlertDialog.Builder(this)
                                    .setTitle("اكتملت الاستعادة")
                                    .setMessage("تم فحص سلامة SQLite والعلاقات وعدد السجلات "
                                            + "وتوازن القيود وأرصدة الصندوق قبل اعتماد النسخة. "
                                            + "حُفظت القاعدة السابقة في "
                                            + result.preservedDatabase.getName()
                                            + ". سجّل الدخول مجددًا.")
                                    .setCancelable(false)
                                    .setPositiveButton("تسجيل الدخول", (alert, which) -> {
                                        startActivity(new Intent(this, LoginActivity.class));
                                        finish();
                                    })
                                    .show());
                        } catch (IOException | RuntimeException exception) {
                            if (database != null && !database.isOpen()) {
                                try {
                                    java.io.File databaseFile = getDatabasePath(
                                            AppDatabase.DATABASE_NAME);
                                    if (!databaseFile.isFile() || databaseFile.length() == 0) {
                                        throw new IllegalStateException("لم تُعثر على قاعدة Room "
                                                + "صالحة بعد تعذر الاستعادة؛ لن تُنشأ قاعدة فارغة");
                                    }
                                    database = AppDatabase.open(getApplicationContext());
                                } catch (RuntimeException reopenException) {
                                    exception.addSuppressed(reopenException);
                                }
                            }
                            candidate.close();
                            showDatabaseError("فشلت الاستعادة؛ لم يُعتمد الملف دون تحقق",
                                    exception);
                            runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true));
                        }
                    });
                }));
        dialog.show();
    }

    private long latestLocalDatabaseChangeTime() {
        long latest = 0;
        java.io.File databaseFile = getDatabasePath(AppDatabase.DATABASE_NAME);
        for (java.io.File file : new java.io.File[]{databaseFile,
                new java.io.File(databaseFile.getPath() + "-wal"),
                new java.io.File(databaseFile.getPath() + "-shm")}) {
            if (file.exists()) {
                latest = Math.max(latest, file.lastModified());
            }
        }
        return latest;
    }

    private long countCurrentRows(String table) {
        if (!java.util.Arrays.asList("invoices", "inventory_items", "journal_entries")
                .contains(table)) {
            throw new IllegalArgumentException("جدول غير مسموح");
        }
        android.database.Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT COUNT(*) FROM `" + table + "`");
        try {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("تعذر قراءة عدد السجلات");
            }
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private void requestLegacyImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        importFileLauncher.launch(intent);
    }

    private void importLegacyBackup(Uri uri) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_BACKUPS);
                String json = readDocument(uri);
                LegacyBackupImporter.ImportResult result =
                        LegacyBackupImporter.importJson(database, json);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        new AlertDialog.Builder(this)
                                .setTitle("تم حفظ الأرشيف القديم في Room")
                                .setMessage(result.summary()
                                        + "\n\nحُفظت السجلات القديمة كأرشيف للمراجعة؛ لم تُحوّل "
                                        + "تلقائيًا إلى قيود تشغيلية أو مخزون متاح للبيع.")
                                .setPositiveButton("موافق", null)
                                .show();
                    }
                });
            } catch (IOException | RuntimeException exception) {
                showDatabaseError("فشل استيراد الأرشيف؛ لم يُحذف الملف الأصلي", exception);
            }
        });
    }

    private String readDocument(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IOException("تعذر فتح الملف المحدد");
            }
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IOException("حجم ملف الاستيراد يتجاوز 20 ميغابايت");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void requestLegacyExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "mohasabat_bagalati_legacy_archive.json");
        exportFileLauncher.launch(intent);
    }

    private void exportLegacyBackup(Uri uri) {
        databaseExecutor.execute(() -> {
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_BACKUPS);
                String json = LegacyBackupExporter.exportJson(database);
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) {
                        throw new IOException("تعذر إنشاء ملف التصدير");
                    }
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                runOnUiThread(() -> showMessage("تم تصدير الأرشيف المحفوظ إلى الملف المحدد"));
            } catch (IOException | RuntimeException exception) {
                showDatabaseError("فشل تصدير الأرشيف", exception);
            }
        });
    }

    private LinearLayout createForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, 0);
        return form;
    }

    private EditText addField(LinearLayout form, String hint, String value, boolean numeric) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setInputType(numeric ? android.text.InputType.TYPE_CLASS_NUMBER
                : android.text.InputType.TYPE_CLASS_TEXT);
        form.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    private String pendingKey(String operation, String fingerprint) {
        SharedPreferences preferences = getSharedPreferences(
                "pending_operation_" + operation, MODE_PRIVATE);
        String oldFingerprint = preferences.getString("fingerprint", null);
        String oldKey = preferences.getString("operation_key", null);
        if (fingerprint.equals(oldFingerprint) && oldKey != null && !oldKey.isEmpty()) {
            return oldKey;
        }
        String operationKey = UUID.randomUUID().toString();
        if (!preferences.edit().putString("fingerprint", fingerprint)
                .putString("operation_key", operationKey).commit()) {
            throw new IllegalStateException("تعذر حفظ مفتاح إعادة المحاولة");
        }
        return operationKey;
    }

    private boolean clearPendingKey(String operation) {
        return getSharedPreferences("pending_operation_" + operation, MODE_PRIVATE)
                .edit().clear().commit();
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
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("أدخل عددًا صحيحًا غير سالب");
        }
    }

    private void showDatabaseError(String action, Exception exception) {
        String reason = exception.getMessage();
        runOnUiThread(() -> showMessage(action + ": "
                + (reason == null || reason.trim().isEmpty() ? "خطأ قاعدة بيانات" : reason)));
    }

    private void showMessage(String message) {
        if (!isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null) {
            databaseExecutor.execute(database::close);
        }
        databaseExecutor.shutdown();
    }
}
