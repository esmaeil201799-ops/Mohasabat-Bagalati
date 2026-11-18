package com.esmaeil.groceryapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvForgotPassword, tvAppSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvAppSettings = findViewById(R.id.tvAppSettings); // تفعيل الإعدادات الداخلية

        // التحقق من بيانات الدخول الاحترافية
        btnLogin.setOnClickListener(v -> {
            String user = etUsername.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال اسم المستخدم وكلمة المرور", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "لم يتم إعداد مستخدم آمن بعد؛ لم يتم تسجيل الدخول", Toast.LENGTH_LONG).show();
        });

        // زر إعادة تعيين كلمة المرور (بحجم وتنسيق مرتب ومضبوط)
        tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "استعادة كلمة المرور غير متاحة: لم يتم إعداد إدارة المستخدمين", Toast.LENGTH_LONG).show();
        });

        // تفعيل إعدادات التطبيق الداخلية (إدارة العملة والبيانات بدون إنترنت)
        tvAppSettings.setOnClickListener(v -> showAppSettingsDialog());
    }

    private void showAppSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("إعدادات نظام بقالتي الداخلية");
        builder.setMessage("لم يتم إعداد هذه الخيارات بعد.\n\n• العملة والمستخدمون: غير مهيئين\n• قاعدة البيانات: SQLite محلية غير مشفرة\n• المزامنة السحابية: غير مهيأة");
        builder.setPositiveButton("حفظ وتطبيق", (dialog, which) -> {
            Toast.makeText(this, "لم تُحفظ الإعدادات؛ لا يوجد تخزين إعدادات مهيأ", Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton("إغلاق", null);
        builder.show();
    }
}
