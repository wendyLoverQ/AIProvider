package com.aiprovider.service;

import com.aiprovider.mapper.ContentOperationsMapper;
import com.aiprovider.model.dto.ContentAccountSourcesDTO;
import com.aiprovider.model.dto.ContentCollectionAccountCreateDTO;
import com.aiprovider.model.dto.ContentSourceCreateDTO;
import com.aiprovider.model.vo.ContentItemVO;
import com.aiprovider.model.vo.ContentSourceTestVO;
import com.aiprovider.model.vo.ContentSourceVO;
import com.aiprovider.model.vo.ContentCollectionAccountVO;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ContentSourceService {
    private static final Pattern TWITTER_UID=Pattern.compile("^[0-9]{1,30}$");
    private static final Pattern TWITTER_HANDLE=Pattern.compile("^[A-Za-z0-9_]{1,15}$");
    private final ContentOperationsRepository repository; private final ContentPlatformSecretCipher cipher; private final TwitterTimelineClient twitter;private final TwitterPublicWebClient twitterWeb; private final ObjectMapper json;
    public ContentSourceService(ContentOperationsRepository repository,ContentPlatformSecretCipher cipher,TwitterTimelineClient twitter,TwitterPublicWebClient twitterWeb,ObjectMapper json){this.repository=repository;this.cipher=cipher;this.twitter=twitter;this.twitterWeb=twitterWeb;this.json=json;}

    @Transactional
    public ContentSourceVO create(ContentSourceCreateDTO dto){
        if(dto==null)throw new IllegalArgumentException("内容源配置不能为空");String platform=upper(dto.getPlatform(),"内容平台");if(!"TWITTER".equals(platform))throw new IllegalArgumentException("当前版本只支持 Twitter 内容源");
        if(dto.getCollectionAccountId()==null)throw new IllegalArgumentException("请选择采集账号");Map<String,Object> collectionAccount=repository.findCollectionAccount(dto.getCollectionAccountId());if(collectionAccount==null||!truth(collectionAccount.get("enabled")))throw new IllegalArgumentException("采集账号不存在或已停用");
        String adapter=text(collectionAccount.get("adapterType"));if(!platform.equals(text(collectionAccount.get("platform"))))throw new IllegalArgumentException("采集账号与内容平台不匹配");
        String uid=null;String handle=null;
        if("TWITTER_API".equals(adapter)){uid=required(dto.getExternalUid(),"Twitter UID",30);if(!TWITTER_UID.matcher(uid).matches())throw new IllegalArgumentException("Twitter UID 必须是数字用户 ID");}
        else if("TWITTER_WEB".equals(adapter)){handle=required(dto.getExternalHandle(),"Twitter 用户名",16);if(handle.startsWith("@"))handle=handle.substring(1);if(!TWITTER_HANDLE.matcher(handle).matches())throw new IllegalArgumentException("Twitter 用户名格式不正确");}
        else throw new IllegalArgumentException("采集账号适配器不受支持");
        Map<String,Object> settings=repository.findSettings();int interval=dto.getPollIntervalMinutes()==null?number(settings==null?null:settings.get("crawlIntervalMinutes"),240):dto.getPollIntervalMinutes();range(interval,15,10080,"采集周期");int fetchLimit=5;
        ContentOperationsMapper.SourceRecord record=new ContentOperationsMapper.SourceRecord();record.setPlatform(platform);record.setSourceType("PROFILE");record.setExternalUid(uid);record.setExternalHandle(handle);record.setAdapterType(adapter);record.setName(required(dto.getName(),"内容源名称",120));record.setSourceUrl(handle==null?"https://x.com/i/user/"+uid:"https://x.com/"+handle);record.setPollIntervalMinutes(interval);record.setFetchLimit(fetchLimit);
        long id=repository.insertSource(record);repository.insertSourceCollectionAccount(id,dto.getCollectionAccountId());return source(id);
    }

    @Transactional
    public ContentCollectionAccountVO createCollectionAccount(ContentCollectionAccountCreateDTO dto){
        if(dto==null)throw new IllegalArgumentException("采集账号配置不能为空");String adapter=upper(dto.getAdapterType(),"采集方式");String raw;
        if("TWITTER_WEB".equals(adapter))raw=twitterWeb.normalizeCredential(required(dto.getAccessToken(),"X Cookie",20000));
        else if("TWITTER_API".equals(adapter)){raw=required(dto.getAccessToken(),"Twitter Bearer Token",1000);if(raw.length()<20)throw new IllegalArgumentException("Twitter Bearer Token 格式不正确");}
        else throw new IllegalArgumentException("采集账号适配器不受支持");
        ContentOperationsMapper.CollectionAccountRecord record=new ContentOperationsMapper.CollectionAccountRecord();record.setDisplayName(required(dto.getDisplayName(),"采集账号名称",100));record.setAdapterType(adapter);record.setCredentialEncrypted(cipher.encrypt(raw));record.setCredentialHint("TWITTER_WEB".equals(adapter)?"Cookie 会话":hint(raw));long id=repository.insertCollectionAccount(record);return collectionAccount(id);
    }

    private ContentCollectionAccountVO collectionAccount(long id){Map<String,Object> r=repository.findCollectionAccount(id);if(r==null)throw new IllegalStateException("采集账号创建后无法读取");return new ContentCollectionAccountVO(number(r.get("id")),text(r.get("platform")),text(r.get("displayName")),text(r.get("adapterType")),text(r.get("credentialEncrypted"))!=null,text(r.get("credentialHint")),truth(r.get("enabled")));}

    public ContentSourceTestVO testFetch(long sourceId){
        Map<String,Object> row=requiredSource(sourceId);String platform=text(row.get("platform"));String adapter=text(row.get("adapterType"));if(!"TWITTER".equals(platform)||(!"TWITTER_API".equals(adapter)&&!"TWITTER_WEB".equals(adapter)))throw new IllegalArgumentException("该内容源不支持 Twitter 测试拉取");
        try{List<TwitterFetchedPost> posts;String encrypted=text(row.get("credentialEncrypted"));if(encrypted==null)throw new ContentSourceException("CREDENTIAL_MISSING","TWITTER_WEB".equals(adapter)?"X Cookie 尚未配置":"Twitter Bearer Token 尚未配置");if("TWITTER_WEB".equals(adapter))posts=Collections.singletonList(twitterWeb.fetchLatest(text(row.get("externalHandle")),cipher.decrypt(encrypted)));else{List<TwitterFetchedPost> window=twitter.fetch(text(row.get("externalUid")),cipher.decrypt(encrypted),integer(row.get("fetchLimit")));posts=window.isEmpty()?Collections.emptyList():Collections.singletonList(window.get(0));}int newCount=0;for(TwitterFetchedPost post:posts){ContentOperationsMapper.ContentItemRecord item=new ContentOperationsMapper.ContentItemRecord();item.setSourceId(sourceId);item.setExternalId(post.id);item.setSourceUrl(post.url);item.setAuthorName(text(row.get("name")));item.setRawText(post.text);item.setRawPayloadJson(raw(post));item.setPublishedAt(post.publishedAt);item.setFetchedByRunType("MANUAL_TEST");newCount+=repository.insertContentItem(item);}repository.markSourceTestSucceeded(sourceId);return new ContentSourceTestVO(sourceId,posts.size(),newCount,LocalDateTime.now(),items(sourceId,1));}
        catch(RuntimeException e){repository.markSourceTestFailed(sourceId,limit(e.getMessage(),500));throw e;}
    }

    public List<ContentItemVO> items(long sourceId,int limit){requiredSource(sourceId);int safe=Math.max(1,Math.min(limit,100));List<ContentItemVO> result=new ArrayList<>();for(Map<String,Object> r:repository.findContentItems(sourceId,safe))result.add(itemFrom(r));return result;}

    public List<Long> accountSourceIds(long accountId){if(repository.findAccount(accountId)==null)throw new IllegalArgumentException("小红书账号不存在");return repository.findAccountSourceIds(accountId);}

    @Transactional
    public List<Long> bindAccountSources(long accountId,ContentAccountSourcesDTO dto){if(repository.findAccount(accountId)==null)throw new IllegalArgumentException("小红书账号不存在");List<Long> sourceIds=dto==null||dto.getSourceIds()==null?Collections.emptyList():new ArrayList<>(new LinkedHashSet<>(dto.getSourceIds()));for(Long id:sourceIds)if(id==null||!repository.isEnabledSource(id))throw new IllegalArgumentException("内容源不存在或未启用："+id);repository.deleteAccountSources(accountId);for(Long id:sourceIds)repository.insertAccountSource(accountId,id);return repository.findAccountSourceIds(accountId);}

    private ContentSourceVO source(long id){return sourceFrom(requiredSource(id));} private Map<String,Object> requiredSource(long id){Map<String,Object> row=repository.findSource(id);if(row==null)throw new IllegalArgumentException("内容源不存在");return row;}
    private ContentSourceVO sourceFrom(Map<String,Object> r){String encrypted=text(r.get("credentialEncrypted"));return new ContentSourceVO(number(r.get("id")),text(r.get("platform")),text(r.get("name")),text(r.get("sourceType")),text(r.get("externalUid")),text(r.get("externalHandle")),text(r.get("adapterType")),text(r.get("sourceUrl")),number(r.get("collectionAccountId")),text(r.get("collectionAccountName")),encrypted!=null,text(r.get("credentialHint")),integer(r.get("pollIntervalMinutes")),integer(r.get("fetchLimit")),truth(r.get("enabled")),text(r.get("lastStatus")),time(r.get("lastCollectedAt")),time(r.get("lastTestedAt")));}
    private ContentItemVO itemFrom(Map<String,Object> r){return new ContentItemVO(number(r.get("id")),number(r.get("sourceId")),text(r.get("externalId")),text(r.get("sourceUrl")),text(r.get("authorName")),text(r.get("rawText")),time(r.get("publishedAt")),text(r.get("processingStatus")),text(r.get("relevanceStatus")),decimal(r.get("relevanceScore")),text(r.get("relevanceReason")),time(r.get("relevanceCheckedAt")),time(r.get("collectedAt")));}
    private String raw(TwitterFetchedPost post){try{return json.writeValueAsString(post.raw);}catch(JsonProcessingException e){throw new IllegalStateException("Twitter 原始数据无法序列化",e);}}
    private String upper(String value,String label){return required(value,label,40).toUpperCase(Locale.ROOT);} private String required(String value,String label,int max){String v=value==null?"":value.trim();if(v.isEmpty())throw new IllegalArgumentException(label+"不能为空");if(v.length()>max)throw new IllegalArgumentException(label+"长度不能超过 "+max);return v;}
    private void range(int value,int min,int max,String label){if(value<min||value>max)throw new IllegalArgumentException(label+"必须在 "+min+" 到 "+max+" 之间");} private String hint(String token){return "••••"+token.substring(Math.max(0,token.length()-4));}
    private String limit(String value,int max){if(value==null)return "未知错误";return value.length()<=max?value:value.substring(0,max);} private String text(Object v){return v==null?null:String.valueOf(v);} private Long number(Object v){return v==null?null:((Number)v).longValue();} private int number(Object v,int fallback){return v==null?fallback:((Number)v).intValue();} private int integer(Object v){return v==null?0:((Number)v).intValue();} private java.math.BigDecimal decimal(Object v){return v==null?null:(v instanceof java.math.BigDecimal?(java.math.BigDecimal)v:new java.math.BigDecimal(String.valueOf(v)));} private boolean truth(Object v){return v instanceof Boolean?(Boolean)v:v!=null&&((Number)v).intValue()!=0;} private LocalDateTime time(Object v){if(v instanceof LocalDateTime)return (LocalDateTime)v;if(v instanceof java.sql.Timestamp)return ((java.sql.Timestamp)v).toLocalDateTime();return null;}
}
