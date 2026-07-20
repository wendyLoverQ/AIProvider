package com.aiprovider.service;

import com.aiprovider.repository.PlatformAccountRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAccountValidationServiceTest {
    @Test void validatesGeminiWithARealModelListProbeAndUpdatesStatus(){
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);PlatformAccountCredentialService credentials=mock(PlatformAccountCredentialService.class);GeminiContentClient gemini=mock(GeminiContentClient.class);
        when(repository.findAccount(9L)).thenReturn(Map.of("id",9L,"platform","GEMINI","adapterType","GEMINI_API","publicConfigJson","{\"apiBaseUrl\":\"https://generativelanguage.googleapis.com\"}","enabled",true));
        when(credentials.requireSecret(9L,"GEMINI","API_KEY")).thenReturn("key");when(gemini.validateApiKey("https://generativelanguage.googleapis.com","key")).thenReturn(true);
        PlatformAccountValidationService service=new PlatformAccountValidationService(repository,credentials,mock(TwitterWebPublisher.class),mock(TwitterPublicWebClient.class),mock(TwitterTimelineClient.class),mock(XiaohongshuWebAdapter.class),mock(DouyinWebAdapter.class),gemini,new com.fasterxml.jackson.databind.ObjectMapper());
        assertDoesNotThrow(()->service.validate(9L));verify(repository).updateStatus(9L,"CONNECTED",null,true,null,null);
    }
    @Test void doesNotTryAnotherCredentialWhenTheConfiguredXAdapterSecretIsMissing(){
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);PlatformAccountCredentialService credentials=mock(PlatformAccountCredentialService.class);
        when(repository.findAccount(6L)).thenReturn(Map.of("id",6L,"platform","X","adapterType","TWITTER_WEB_PUBLISHER","enabled",true));when(credentials.requireSecret(6L,"X","STORAGE_STATE")).thenThrow(new IllegalStateException("CREDENTIAL_MISSING"));
        PlatformAccountValidationService service=new PlatformAccountValidationService(repository,credentials,mock(TwitterWebPublisher.class),mock(TwitterPublicWebClient.class),mock(TwitterTimelineClient.class),mock(XiaohongshuWebAdapter.class),mock(DouyinWebAdapter.class),mock(GeminiContentClient.class),new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals("CREDENTIAL_MISSING",assertThrows(IllegalStateException.class,()->service.validate(6L)).getMessage());verify(credentials,never()).requireSecret(6L,"X","COOKIE");
    }
    @Test void rejectsDisabledAccountsWithoutOverwritingTheirDisabledState(){
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);PlatformAccountCredentialService credentials=mock(PlatformAccountCredentialService.class);
        when(repository.findAccount(4L)).thenReturn(Map.of("id",4L,"platform","XIAOHONGSHU","adapterType","XIAOHONGSHU_WEB","enabled",false));
        PlatformAccountValidationService service=new PlatformAccountValidationService(repository,credentials,mock(TwitterWebPublisher.class),mock(TwitterPublicWebClient.class),mock(TwitterTimelineClient.class),mock(XiaohongshuWebAdapter.class),mock(DouyinWebAdapter.class),mock(GeminiContentClient.class),new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals("ACCOUNT_DISABLED",assertThrows(IllegalStateException.class,()->service.validate(4L)).getMessage());
        verify(repository,never()).updateStatus(anyLong(),anyString(),any(),anyBoolean(),any(),any());verifyNoInteractions(credentials);
    }
}
