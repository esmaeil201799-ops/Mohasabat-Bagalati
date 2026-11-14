package com.esmaeil.groceryapp;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerItems, spinnerPaymentType;
    private EditText etQuantity;
    private Button btnCheckout, btnShareReport, btnPrintReport;
    private TextView tvTotalSalesVal, tvCashVal;

    private double totalSalesToday = 25000.0;
    private double cashBox = 15000.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupSpinners();
        setupListeners();
        updateFinancialUI();
    }

    private void initializeViews() {
        try {
            spinnerItems = findViewById(R.id.spinnerItems);
            spinnerPaymentType = findViewById(R.id.spinnerPaymentType);
            etQuantity = findViewById(R.id.etQuantity);
            btnCheckout = findViewById(R.id.btnCheckout);
            btnShareReport = findViewById(R.id.btnShareReport);
            btnPrintReport = findViewById(R.id.btnPrintReport);
            tvTotalSalesVal = findViewById(R.id.tvTotalSalesVal);
            tvCashVal = findViewById(R.id.tvCashVal);
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في تهيئة الواجهات: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupSpinners() {
        // إعداد قائمة الأصناف
        String[] itemsList = {"كيس أرز (متوفر: 10 - سعر: 25000)", "دقيق السنابل (متوفر: 15 - سعر: 20000)", "سكر دباغ (متوفر: 8 - سعر: 30000)"};
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, itemsList);
        spinnerItems.setAdapter(itemsAdapter);

        // إعداد طرق الدفع المحسنة (نقداً، أجل، بنك/محفظة)
        String[] paymentTypes = {"نقداً (كاش / صندوق)", "أجل (حساب عميل / ديون)", "محفظة رقمية / تحويل بنكي"};
        ArrayAdapter<String> paymentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, paymentTypes);
        spinnerPaymentType.setAdapter(paymentAdapter);
    }

    private void setupListeners() {
        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(v -> processSaleTransaction());
        }

        if (btnShareReport != null) {
            btnShareReport.setOnClickListener(v -> {
                String report = ReportHelper.generateDailySalesReport(
                        "تموينات أبو باسل",
                        totalSalesToday + " ر.ي",
                        cashBox + " ر.ي",
                        "10,000 ر.ي"
                );
                ReportHelper.shareReport(this, report);
            });
        }

        if (btnPrintReport != null) {
            btnPrintReport.setOnClickListener(v -> 
                Toast.makeText(this, "جاري تهيئة الطابعة الحرارية وتصدير المستند...", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void processSaleTransaction() {
        try {
            String qtyStr = etQuantity != null ? etQuantity.getText().toString().trim() : "1";
            int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

            // محاكاة تحديث المؤشرات المالية والدورة المستندية
            totalSalesToday += (25000 * qty);
            cashBox += (15000 * qty);

            updateFinancialUI();
            Toast.makeText(this, "تم إتمام عملية البيع وتحديث المؤشرات بنجاح", Toast.LENGTH_SHORT).show();
            
            if (etQuantity != null) {
                etQuantity.setText("");
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في تنفيذ عملية البيع: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFinancialUI() {
        if (tvTotalSalesVal != null) {
            tvTotalSalesVal.setText(totalSalesToday + " ر.ي");
        }
        if (tvCashVal != null) {
            tvCashVal.setText(cashBox + " ر.ي");
        }
    }
}
