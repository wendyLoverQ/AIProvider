package com.aiprovider.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class XiaohongshuWebAdapterTest {
    @Test void recognizesCreatorHomeAsCompletedQrLogin() {
        assertTrue(XiaohongshuWebAdapter.isCreatorHomeUrl("https://creator.xiaohongshu.com/creator/home?source=official"));
        assertTrue(XiaohongshuWebAdapter.isCreatorHomeUrl("https://creator.xiaohongshu.com/new/home"));
        assertFalse(XiaohongshuWebAdapter.isCreatorHomeUrl("https://creator.xiaohongshu.com/login?source=official"));
    }

    @Test void recognizesCurrentCreatorAuthenticationCookie() {
        assertTrue(XiaohongshuWebAdapter.isAuthenticatedCookieName("access-token-creator.xiaohongshu.com"));
        assertTrue(XiaohongshuWebAdapter.isAuthenticatedCookieName("web_session"));
        assertFalse(XiaohongshuWebAdapter.isAuthenticatedCookieName("a1"));
    }

    @Test void recognizesOnlyCreatorLoginRouteAsExpiredSession() {
        assertTrue(XiaohongshuWebAdapter.isLoginUrl("https://creator.xiaohongshu.com/login?source=official"));
        assertFalse(XiaohongshuWebAdapter.isLoginUrl("https://creator.xiaohongshu.com/publish/publish?source=official"));
    }

    @Test void readsCurrentCreatorQrProtocolStatusWithoutReadingTokens() {
        assertEquals(2, XiaohongshuWebAdapter.qrCodeStatus("{\"data\":{\"codeStatus\":2,\"ticket\":\"secret\"}}"));
        assertEquals(1, XiaohongshuWebAdapter.qrCodeStatus("{\"codeStatus\":1}"));
        assertEquals(-1, XiaohongshuWebAdapter.qrCodeStatus("{\"result\":0}"));
    }

    @Test void diagnosticsKeepOnlySafeBusinessFieldsAndCookieNames() {
        String body="{\"codeStatus\":2,\"result\":0,\"success\":true,\"ticket\":\"secret-ticket\"}";
        String fields=XiaohongshuWebAdapter.safeResponseFields(body);
        assertTrue(fields.contains("codeStatus=2"));assertTrue(fields.contains("result=0"));assertFalse(fields.contains("secret-ticket"));
        assertEquals(new TreeSet<>(Arrays.asList("web_session","a1")),XiaohongshuWebAdapter.setCookieNames(Arrays.asList("web_session=secret; Path=/","a1=value; Secure")));
    }
}
