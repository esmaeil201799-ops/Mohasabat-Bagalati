package com.esmaeil.groceryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppIdentityTest {
    @Test
    public void declaresArabicIdentityAndCopyright() {
        assertEquals("محاسبة بقالتي", AppIdentity.APP_NAME);
        assertEquals("إسماعيل أبكر", AppIdentity.OWNER);
        assertEquals("© 2026 إسماعيل أبكر. جميع الحقوق محفوظة.", AppIdentity.COPYRIGHT);
        assertTrue(AppIdentity.CONTACT_EMAIL.isEmpty());
    }
}
