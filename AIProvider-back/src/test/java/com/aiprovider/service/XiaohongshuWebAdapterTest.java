package com.aiprovider.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class XiaohongshuWebAdapterTest {
    @Test void recognizesCreatorHomeAsCompletedQrLogin() {
        assertTrue(XiaohongshuWebAdapter.isCreatorHomeUrl("https://creator.xiaohongshu.com/creator/home?source=official"));
        assertFalse(XiaohongshuWebAdapter.isCreatorHomeUrl("https://creator.xiaohongshu.com/login?source=official"));
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
}
