package com.aiprovider.service;

import com.aiprovider.mapper.ContentOperationsMapper;
import com.aiprovider.model.dto.ContentSourceCreateDTO;
import com.aiprovider.model.dto.ContentCollectionAccountCreateDTO;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentSourceServiceTest {
    @Test void createsReusableCollectionAccountAndStoresNormalizedCookieOnce(){
        ContentOperationsRepository repository=mock(ContentOperationsRepository.class);ContentPlatformSecretCipher cipher=mock(ContentPlatformSecretCipher.class);TwitterPublicWebClient web=mock(TwitterPublicWebClient.class);ContentCollectionAccountCreateDTO dto=new ContentCollectionAccountCreateDTO();dto.setDisplayName("主 X 账号");dto.setAdapterType("TWITTER_WEB");dto.setAccessToken("auth_token=a; ct0=b; ignored=c");
        when(web.normalizeCredential(dto.getAccessToken())).thenReturn("{\"auth_token\":\"a\",\"ct0\":\"b\"}");when(cipher.encrypt(anyString())).thenReturn("encrypted");when(repository.insertCollectionAccount(any())).thenReturn(3L);Map<String,Object> saved=new HashMap<>();saved.put("id",3L);saved.put("platform","TWITTER");saved.put("displayName","主 X 账号");saved.put("adapterType","TWITTER_WEB");saved.put("credentialEncrypted","encrypted");saved.put("credentialHint","Cookie 会话");saved.put("enabled",true);when(repository.findCollectionAccount(3L)).thenReturn(saved);
        assertEquals("主 X 账号",new ContentSourceService(repository,cipher,mock(TwitterTimelineClient.class),web,new ObjectMapper()).createCollectionAccount(dto).getDisplayName());verify(cipher).encrypt("{\"auth_token\":\"a\",\"ct0\":\"b\"}");
    }
    @Test void createsWebSourceByBindingAReusableCollectionAccount(){
        ContentOperationsRepository repository=mock(ContentOperationsRepository.class);ContentPlatformSecretCipher cipher=mock(ContentPlatformSecretCipher.class);TwitterPublicWebClient web=mock(TwitterPublicWebClient.class);ContentSourceCreateDTO dto=new ContentSourceCreateDTO();dto.setPlatform("TWITTER");dto.setCollectionAccountId(3L);dto.setExternalHandle("@elonmusk");dto.setName("Elon Musk");
        Map<String,Object> account=new HashMap<>();account.put("id",3L);account.put("platform","TWITTER");account.put("adapterType","TWITTER_WEB");account.put("enabled",true);when(repository.findCollectionAccount(3L)).thenReturn(account);
        when(repository.findSettings()).thenReturn(Collections.singletonMap("crawlIntervalMinutes",60));when(repository.insertSource(any())).thenReturn(9L);Map<String,Object> saved=new HashMap<>();saved.put("id",9L);saved.put("platform","TWITTER");saved.put("sourceType","PROFILE");saved.put("adapterType","TWITTER_WEB");saved.put("externalHandle","elonmusk");saved.put("sourceUrl","https://x.com/elonmusk");saved.put("collectionAccountId",3L);saved.put("collectionAccountName","主 X 账号");saved.put("credentialEncrypted","encrypted");saved.put("pollIntervalMinutes",60);saved.put("fetchLimit",5);saved.put("enabled",true);when(repository.findSource(9L)).thenReturn(saved);
        assertEquals("elonmusk",new ContentSourceService(repository,cipher,mock(TwitterTimelineClient.class),web,new ObjectMapper()).create(dto).getExternalHandle());
        verify(repository).insertSource(argThat((ContentOperationsMapper.SourceRecord row)->row.getExternalUid()==null&&"elonmusk".equals(row.getExternalHandle())&&"https://x.com/elonmusk".equals(row.getSourceUrl())&&row.getCredentialEncrypted()==null&&row.getPollIntervalMinutes()==60));verify(repository).insertSourceCollectionAccount(9L,3L);
    }
    @Test void storesOnlyNewestPostWhenTwitterReturnsSeveral(){
        ContentOperationsRepository repository=mock(ContentOperationsRepository.class);ContentPlatformSecretCipher cipher=mock(ContentPlatformSecretCipher.class);TwitterTimelineClient twitter=mock(TwitterTimelineClient.class);ObjectMapper json=new ObjectMapper();
        Map<String,Object> source=new HashMap<>();source.put("id",7L);source.put("platform","TWITTER");source.put("adapterType","TWITTER_API");source.put("externalUid","44196397");source.put("credentialEncrypted","encrypted");source.put("fetchLimit",5);source.put("name","Elon Musk");when(repository.findSource(7)).thenReturn(source);when(cipher.decrypt("encrypted")).thenReturn("bearer");
        TwitterFetchedPost newest=new TwitterFetchedPost("2","newest","https://x.com/2",LocalDateTime.now(),json.createObjectNode());TwitterFetchedPost older=new TwitterFetchedPost("1","older","https://x.com/1",LocalDateTime.now().minusHours(1),json.createObjectNode());when(twitter.fetch("44196397","bearer",5)).thenReturn(Arrays.asList(newest,older));when(repository.insertContentItem(any())).thenReturn(1);when(repository.findContentItems(7,1)).thenReturn(Collections.emptyList());
        assertEquals(1,new ContentSourceService(repository,cipher,twitter,mock(TwitterPublicWebClient.class),json).testFetch(7).getFetchedCount());
        verify(repository,times(1)).insertContentItem(argThat((ContentOperationsMapper.ContentItemRecord item)->"2".equals(item.getExternalId())));
    }
    @Test void fetchesWebSourceWithDecryptedSession(){ContentOperationsRepository repository=mock(ContentOperationsRepository.class);ContentPlatformSecretCipher cipher=mock(ContentPlatformSecretCipher.class);TwitterPublicWebClient web=mock(TwitterPublicWebClient.class);ObjectMapper json=new ObjectMapper();Map<String,Object> source=new HashMap<>();source.put("id",8L);source.put("platform","TWITTER");source.put("adapterType","TWITTER_WEB");source.put("externalHandle","elonmusk");source.put("credentialEncrypted","encrypted");source.put("name","Elon Musk");when(repository.findSource(8)).thenReturn(source);when(cipher.decrypt("encrypted")).thenReturn("normalized-cookie");when(web.fetchLatest("elonmusk","normalized-cookie")).thenReturn(new TwitterFetchedPost("3","session latest","https://x.com/elonmusk/status/3",LocalDateTime.now(),json.createObjectNode()));when(repository.insertContentItem(any())).thenReturn(1);when(repository.findContentItems(8,1)).thenReturn(Collections.emptyList());assertEquals(1,new ContentSourceService(repository,cipher,mock(TwitterTimelineClient.class),web,json).testFetch(8).getNewCount());verify(web).fetchLatest("elonmusk","normalized-cookie");}
}
