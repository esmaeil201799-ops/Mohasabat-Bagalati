package com.esmaeil.groceryapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

public class MainActivity extends AppCompatActivity {

    private TextView tvCashBalance, tvTodaySales;
    private Button btnExecuteSale, btnPrintStatement, btnShareReport;
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCashBalance = findViewById(R.id.tvCashBalance);
        tvTodaySales = findViewById(R.id.tvTodaySales);
        btnExecuteSale = findViewById(R.id.btnExecuteSale);
        btnPrintStatement = findViewById(R.id.btnPrintStatement);
        btnShareReport = findViewById(R.id.btnShareReport);

        // تهيئة قاعدة البيانات المحلية (Room)
        database = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "grocery_erp_db")
                .addMigrations(AppDatabase.MIGRATION_3_4)
                .build();

        loadDashboardData();
        btnExecuteSale.setOnClickListener(v -> executeRealSaleProcess());

        btnPrintStatement.setOnClickListener(v -> showUnavailableReport());
        btnShareReport.setOnClickListener(v -> showUnavailableReport());
    }

    private void loadDashboardData() {
        tvCashBalance.setText("غير متاح");
        tvTodaySales.setText("غير متاح");
    }

    private void executeRealSaleProcess() {
        Toast.makeText(this, "لم تُحفظ أي عملية: البيع غير مربوط حاليًا بقاعدة البيانات", Toast.LENGTH_LONG).show();
    }

    private void showUnavailableReport() {
        Toast.makeText(this, "التقارير والطباعة غير متاحة قبل ربطها بقيود قاعدة البيانات", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        database.close();
    }
}
