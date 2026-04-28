package com.tidao.wuxia.app.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainActivityEmailTest {

    @Test
    public void isValidEmailAddress_acceptsCommonEmail() {
        assertTrue(MainActivity.isValidEmailAddress("friend@qq.com"));
        assertTrue(MainActivity.isValidEmailAddress("user.name@example.co"));
    }

    @Test
    public void isValidEmailAddress_rejectsInvalidEmail() {
        assertFalse(MainActivity.isValidEmailAddress(""));
        assertFalse(MainActivity.isValidEmailAddress("bad-email"));
        assertFalse(MainActivity.isValidEmailAddress("@qq.com"));
        assertFalse(MainActivity.isValidEmailAddress("friend@"));
        assertFalse(MainActivity.isValidEmailAddress("friend@qq"));
        assertFalse(MainActivity.isValidEmailAddress("friend name@qq.com"));
    }
}
