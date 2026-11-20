package com.esmaeil.groceryapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ((TextView) findViewById(R.id.tvAboutAppName)).setText(AppIdentity.APP_NAME);
        ((TextView) findViewById(R.id.tvAboutOwner)).setText("صاحب المشروع: " + AppIdentity.OWNER);
        ((TextView) findViewById(R.id.tvAboutDescription)).setText(AppIdentity.DESCRIPTION);
        ((TextView) findViewById(R.id.tvAboutVersion)).setText(
                "الإصدار: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        ((TextView) findViewById(R.id.tvAboutReleaseDate)).setText(
                "تاريخ الإصدار: " + AppIdentity.RELEASE_DATE);
        ((TextView) findViewById(R.id.tvAboutCopyright)).setText(AppIdentity.COPYRIGHT);
        ((TextView) findViewById(R.id.tvAboutContact)).setText(AppIdentity.CONTACT_EMAIL.isEmpty()
                ? "التواصل الرسمي: غير مهيأ"
                : "البريد الرسمي: " + AppIdentity.CONTACT_EMAIL);
    }
}
