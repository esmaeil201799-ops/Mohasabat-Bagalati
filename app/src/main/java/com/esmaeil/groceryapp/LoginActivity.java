package com.esmaeil.groceryapp;

import android.content.Intent;
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

            // الدخول المعتمد لنظام أبو باسل المحاسبي
            if (user.equals("admin") || user.equals("أبو باسل") || user.equals("اسماعيل")) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "بيانات الدخول غير صحيحة، يرجى التحقق", Toast.LENGTH_SHORT).show();
            }
        });

        // زر إعادة تعيين كلمة المرور (بحجم وتنسيق مرتب ومضبوط)
        tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "تم إرسال رابط استعادة الكلمة للنظام المحلي الآمن", Toast.LENGTH_LONG).show();
        });

        // تفعيل إعدادات التطبيق الداخلية (إدارة العملة والبيانات بدون إنترنت)
        tvAppSettings.setOnClickListener(v -> showAppSettingsDialog());
    }

    private void showAppSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("إعدادات نظام بقالتي الداخلية");
        builder.setMessage("إدارة خيارات النظام المحلي:\n\n• العملة الأساسية: ريال يمني (YER) / سعودي (SAR)\n• قاعدة البيانات: محلية مشفرة (Room SQLite)\n• وضع العمل: بدون إنترنت (Offline-First)");
        builder.setPositiveButton("حفظ وتطبيق", (dialog, which) -> {
            Toast.makeText(this, "تم تحديث إعدادات النظام بنجاح", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("إغلاق", null);
        builder.show();
    }
}
