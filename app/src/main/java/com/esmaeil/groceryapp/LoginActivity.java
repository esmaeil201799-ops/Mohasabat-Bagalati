package com.esmaeil.groceryapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etPinConfirm;
    private Button btnLogin;
    private Button btnBiometric;
    private TextView tvForgotPassword;
    private boolean ownerSetupRequired;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        database = AppDatabase.open(getApplicationContext());

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etPinConfirm = findViewById(R.id.etPinConfirm);
        btnLogin = findViewById(R.id.btnLogin);
        btnBiometric = findViewById(R.id.btnBiometric);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        findViewById(R.id.tvAboutApp).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        btnLogin.setEnabled(false);
        btnLogin.setOnClickListener(v -> authenticateOrInitializeOwner());
        tvForgotPassword.setOnClickListener(v -> showRecoveryDialog());
        btnBiometric.setOnClickListener(v -> loginWithBiometric());
        findViewById(R.id.tvAppSettings).setOnClickListener(v -> showAppSettingsDialog());
        refreshSetupMode();
    }

    private void refreshSetupMode() {
        databaseExecutor.execute(() -> {
            boolean noUsers = database.erpDao().countAppUsers() == 0;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                ownerSetupRequired = noUsers;
                etUsername.setHint(noUsers ? "اسم مستخدم المالك الجديد" : "اسم المستخدم");
                etPassword.setHint("PIN (6 إلى 12 رقمًا)");
                etPassword.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                etPinConfirm.setVisibility(noUsers ? EditText.VISIBLE : EditText.GONE);
                btnLogin.setText(noUsers ? "تهيئة حساب المالك" : "تسجيل الدخول");
                tvForgotPassword.setVisibility(noUsers ? TextView.GONE : TextView.VISIBLE);
                boolean canUseBiometric = BiometricManager.from(this).canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        == BiometricManager.BIOMETRIC_SUCCESS;
                btnBiometric.setVisibility(!noUsers && canUseBiometric
                        && BiometricLogin.hasCredential(this) ? Button.VISIBLE : Button.GONE);
                btnLogin.setEnabled(true);
            });
        });
    }

    private void authenticateOrInitializeOwner() {
        String username = etUsername.getText().toString();
        char[] pin = etPassword.getText().toString().toCharArray();
        char[] confirmation = etPinConfirm.getText().toString().toCharArray();
        if (ownerSetupRequired && !Arrays.equals(pin, confirmation)) {
            showMessage("تأكيد PIN غير مطابق");
            return;
        }

        btnLogin.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                String recoveryCode = null;
                if (ownerSetupRequired) {
                    recoveryCode = new AuthService(database).createOwner(username, pin);
                } else {
                    new AuthService(database).login(username, pin);
                }
                String oneTimeCode = recoveryCode;
                runOnUiThread(() -> {
                    clearPinFields();
                    if (isFinishing()) {
                        return;
                    }
                    if (oneTimeCode == null) {
                        offerBiometricSetup(SessionManager.authenticatedUserId());
                    } else {
                        showRecoveryCode(oneTimeCode, "رمز استعادة المالك",
                                () -> offerBiometricSetup(SessionManager.authenticatedUserId()));
                    }
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    clearPinFields();
                    showMessage(errorMessage(exception));
                    btnLogin.setEnabled(true);
                });
            } finally {
                Arrays.fill(pin, '\0');
                Arrays.fill(confirmation, '\0');
            }
        });
    }

    private void showRecoveryDialog() {
        LinearLayout form = createForm();
        EditText username = addField(form, "اسم المستخدم", false);
        EditText recoveryCode = addField(form, "رمز الاستعادة", false);
        EditText newPin = addField(form, "PIN جديد (6 إلى 12 رقمًا)", true);
        EditText confirmPin = addField(form, "تأكيد PIN الجديد", true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("استعادة الوصول")
                .setMessage("استخدم رمز الاستعادة الذي ظهر عند إعداد الحساب. لا توجد استعادة عبر الإنترنت.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("استعادة PIN", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String recoveryUsername = username.getText().toString();
                    String recoveryValue = recoveryCode.getText().toString();
                    char[] pin = newPin.getText().toString().toCharArray();
                    char[] confirmation = confirmPin.getText().toString().toCharArray();
                    if (!Arrays.equals(pin, confirmation)) {
                        showMessage("تأكيد PIN غير مطابق");
                        Arrays.fill(pin, '\0');
                        Arrays.fill(confirmation, '\0');
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    databaseExecutor.execute(() -> {
                        try {
                            String nextRecoveryCode = new AuthService(database)
                                    .recoverPin(recoveryUsername, recoveryValue, pin);
                            String biometricWarning = null;
                            try {
                                BiometricLogin.clear(getApplicationContext());
                            } catch (RuntimeException exception) {
                                biometricWarning = errorMessage(exception);
                            }
                            String finalBiometricWarning = biometricWarning;
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                if (finalBiometricWarning != null) {
                                    showMessage("تم تغيير PIN؛ " + finalBiometricWarning);
                                }
                                showRecoveryCode(nextRecoveryCode, "رمز استعادة جديد",
                                        () -> offerBiometricSetup(
                                                SessionManager.authenticatedUserId()));
                            });
                        } catch (RuntimeException exception) {
                            runOnUiThread(() -> {
                                showMessage(errorMessage(exception));
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            });
                        } finally {
                            Arrays.fill(pin, '\0');
                            Arrays.fill(confirmation, '\0');
                        }
                    });
                }));
        dialog.show();
    }

    private void showRecoveryCode(String code, String title, Runnable afterSaved) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("اكتب الرمز واحفظه خارج الهاتف في مكان آمن. لن يُعرض مرة أخرى:\n\n" + code)
                .setCancelable(false)
                .setPositiveButton("حفظ الرمز والمتابعة", (dialog, which) -> afterSaved.run())
                .show();
    }

    private void offerBiometricSetup(String userId) {
        if (BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG)
                != BiometricManager.BIOMETRIC_SUCCESS) {
            openMain();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("تسجيل الدخول بالبصمة")
                .setMessage("يمكن ربط هذا الحساب ببصمة الجهاز. لا يُحفظ PIN في إعداد البصمة.")
                .setNegativeButton("ليس الآن", (dialog, which) -> openMain())
                .setPositiveButton("تفعيل", (dialog, which) -> enableBiometric(userId))
                .show();
    }

    private void enableBiometric(String userId) {
        try {
            authenticateBiometric(BiometricLogin.createEncryptCipher(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            try {
                                BiometricLogin.storeEncryptedUser(LoginActivity.this,
                                        result.getCryptoObject().getCipher(), userId);
                            } catch (RuntimeException exception) {
                                showMessage(errorMessage(exception));
                            }
                            openMain();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            showMessage("لم تُفعّل البصمة؛ سيستمر الدخول عبر PIN");
                            openMain();
                        }
                    });
        } catch (RuntimeException exception) {
            showMessage(errorMessage(exception));
            openMain();
        }
    }

    private void loginWithBiometric() {
        try {
            authenticateBiometric(BiometricLogin.createDecryptCipher(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            try {
                                String userId = BiometricLogin.decryptUserId(LoginActivity.this,
                                        result.getCryptoObject().getCipher());
                                databaseExecutor.execute(() -> {
                                    try {
                                        new AuthService(database).loginByBiometric(userId);
                                        runOnUiThread(() -> openMain());
                                    } catch (RuntimeException exception) {
                                        runOnUiThread(() -> showMessage(errorMessage(exception)));
                                    }
                                });
                            } catch (RuntimeException exception) {
                                showMessage(errorMessage(exception));
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            showMessage("تعذر التحقق بالبصمة؛ يمكنك استخدام PIN");
                        }
                    });
        } catch (RuntimeException exception) {
            showMessage(errorMessage(exception));
        }
    }

    private void authenticateBiometric(Cipher cipher,
                                      BiometricPrompt.AuthenticationCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, callback);
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("محاسبة بقالتي")
                .setSubtitle("تحقق من بصمة الجهاز")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("إلغاء")
                .build();
        prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void clearPinFields() {
        etPassword.setText("");
        etPinConfirm.setText("");
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
        field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        form.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    private void showAppSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("إعدادات النظام")
                .setMessage("قاعدة البيانات محلية. لم تُفعّل المزامنة السحابية.")
                .setPositiveButton("إغلاق", null)
                .show();
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "تعذر إكمال العملية" : message;
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
