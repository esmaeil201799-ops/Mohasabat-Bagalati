<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="#F4F6F8"
    android:padding="24dp"
    android:layoutDirection="rtl">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="محاسبة بقالتي 2.0"
        android:textSize="24sp"
        android:textStyle="bold"
        android:textColor="#1B5E20"
        android:layout_marginBottom="8dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="النظام التجاري المدمج لإدارة البقالات"
        android:textSize="14sp"
        android:textColor="#555555"
        android:layout_marginBottom="32dp" />

    <com.google.android.material.textfield.TextInputLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="20dp"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/etPinCode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="أدخل الرمز السري (افتراضي: 1234)"
            android:inputType="numberPassword"
            android:maxLength="6"
            android:textSize="18sp"
            android:gravity="center" />
    </com.google.android.material.textfield.TextInputLayout>

    <Button
        android:id="@+id/btnLogin"
        android:layout_width="match_parent"
        android:layout_height="55dp"
        android:text="دخول للنظام"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="#FFFFFF"
        android:backgroundTint="#2E7D32"
        android:layout_marginBottom="12dp" />

    <Button
        android:id="@+id/btnResetPin"
        android:layout_width="match_parent"
        android:layout_height="45dp"
        android:text="إعادة تعيين الرمز الإفتراضي"
        android:textSize="14sp"
        android:textColor="#2E7D32"
        style="?attr/materialButtonOutlinedStyle" />

</LinearLayout>
