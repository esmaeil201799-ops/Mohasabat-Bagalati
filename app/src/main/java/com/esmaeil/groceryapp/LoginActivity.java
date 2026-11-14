package com.esmaeil.groceryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etPinCode;
    private Button btnLogin;
    private Button btnResetPin;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        try {
            etPinCode = findViewById(R.id.etPinCode);
            btnLogin = findViewById(R.id.btnLogin);
            btnResetPin = findViewById(R.id.btnResetPin);

            prefs = getSharedPreferences("GroceryPrefs", MODE_PRIVATE);

            if (!prefs.contains("app_pin")) {
                prefs.edit().putString("app_pin", "1234").apply();
            }

            if (btnLogin != null) {
                btnLogin.setOnClickListener(v -> {
                    try {
                        String enteredPin = etPinCode != null ? etPinCode.getText().toString().trim() : "";
                        String savedPin = prefs.getString("app_pin", "1234");

                        if (enteredPin.equals(savedPin)) {
                            Toast.makeText(this, "تم تسجيل الدخول بنجاح", Toast.LENGTH_SHORT).show();
                            
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                            
                        } else {
                            Toast.makeText(this, "الرمز السري غير صحيح (الافتراضي: 1234)", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "خطأ أثناء تسجيل الدخول: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            if (btnResetPin != null) {
                btnResetPin.setOnClickListener(v -> {
                    try {
                        prefs.edit().putString("app_pin", "1234").apply();
                        if (etPinCode != null) {
                            etPinCode.setText("");
                        }
                        Toast.makeText(this, "تم إعادة تعيين الرمز إلى (1234) بنجاح", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "خطأ في التعيين: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

        } catch (Exception e) {
            Toast.makeText(this, "خطأ حرج في التشغيل: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
