package com.esmaeil.groceryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etPinCode;
    private Button btnLogin, btnResetPin;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etPinCode = findViewById(R.id.etPinCode);
        btnLogin = findViewById(R.id.btnLogin);
        btnResetPin = findViewById(R.id.btnResetPin);

        prefs = getSharedPreferences("GroceryPrefs", MODE_PRIVATE);

        if (!prefs.contains("app_pin")) {
            prefs.edit().putString("app_pin", "1234").apply();
        }

        btnLogin.setOnClickListener(v -> {
            String enteredPin = etPinCode.getText() != null ? etPinCode.getText().toString().trim() : "";
            String savedPin = prefs.getString("app_pin", "1234");

            if (enteredPin.equals(savedPin)) {
                Toast.makeText(this, "تم تسجيل الدخول بنجاح", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "الرمز السري غير صحيح!", Toast.LENGTH_SHORT).show();
            }
        });

        btnResetPin.setOnClickListener(v -> {
            prefs.edit().putString("app_pin", "1234").apply();
            Toast.makeText(this, "تمت إعادة تعيين الرمز إلى الإفتراضي (1234)", Toast.LENGTH_LONG).show();
        });
    }
}
