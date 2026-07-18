package com.aiprovider.service;

import com.aiprovider.mapper.ContentOperationsMapper;
import com.aiprovider.model.dto.*;
import com.aiprovider.model.vo.*;
import com.aiprovider.repository.ContentOperationsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ContentOperationsService {
    private static final Set<String> MODES=new HashSet<>(Arrays.asList("AUTO","MANUAL"));
    private static final Set<String> SOURCE_TYPES=new HashSet<>(Arrays.asList("PROFILE","KEYWORD","FEED","URL"));
    private final ContentOperationsRepository repository;
    public ContentOperationsService(ContentOperationsRepository repository){this.repository=repository;}

    public ContentOperationsOverviewVO overview(){
        Map<String,Long> counters=new LinkedHashMap<>();
        counters.put("collectedToday",repository.countCollectedToday()); counters.put("readyDrafts",repository.countReadyDrafts());
        counters.put("publishedToday",repository.countPublishedToday()); counters.put("pendingComments",repository.countPendingComments());
        counters.put("failedPublications",repository.countFailedPublications());
        return new ContentOperationsOverviewVO(settingsFrom(repository.findSettings()),counters,accounts(),sources(),publications());
    }

    @Transactional
    public ContentAccountVO createAccount(ContentAccountCreateDTO dto){
        if(dto==null) throw new IllegalArgumentException("账号配置不能为空");
        ContentOperationsMapper.AccountRecord record=new ContentOperationsMapper.AccountRecord();
        record.setDisplayName(required(dto.getDisplayName(),"账号名称",100)); record.setAccountHandle(optional(dto.getAccountHandle(),120));
        record.setPublishMode(mode(dto.getPublishMode())); long id=repository.insertAccount(record); return account(id);
    }

    @Transactional
    public ContentAccountVO updateAccount(long id,ContentAccountModeDTO dto){
        Map<String,Object> current=repository.findAccount(id); if(current==null) throw new IllegalArgumentException("小红书账号不存在");
        String publishMode=dto!=null&&dto.getPublishMode()!=null?mode(dto.getPublishMode()):text(current.get("publishMode"));
        boolean enabled=dto!=null&&dto.getEnabled()!=null?dto.getEnabled():truth(current.get("enabled"));
        if(!repository.updateAccountMode(id,publishMode,enabled)) throw new IllegalStateException("账号配置更新失败"); return account(id);
    }

    @Transactional
    public ContentSourceVO createSource(ContentSourceCreateDTO dto){
        if(dto==null) throw new IllegalArgumentException("采集源配置不能为空");
        String type=required(dto.getSourceType(),"采集类型",30).toUpperCase(Locale.ROOT); if(!SOURCE_TYPES.contains(type)) throw new IllegalArgumentException("不支持的采集类型");
        String url=required(dto.getSourceUrl(),"采集地址",1000); if(!url.startsWith("https://")) throw new IllegalArgumentException("采集地址必须使用 HTTPS");
        int interval=dto.getPollIntervalMinutes()==null?240:dto.getPollIntervalMinutes(); range(interval,15,10080,"采集周期");
        ContentOperationsMapper.SourceRecord record=new ContentOperationsMapper.SourceRecord(); record.setName(required(dto.getName(),"采集源名称",120));
        record.setSourceType(type);record.setSourceUrl(url);record.setPollIntervalMinutes(interval);long id=repository.insertSource(record);
        for(ContentSourceVO item:sources()) if(item.getId()==id) return item; throw new IllegalStateException("采集源创建后无法读取");
    }

    @Transactional
    public ContentOperationSettingsVO updateSettings(ContentOperationSettingsDTO dto){
        if(dto==null||dto.getAutomationEnabled()==null) throw new IllegalArgumentException("自动运行开关不能为空");
        Map<String,Object> currentSettings=repository.findSettings();if(currentSettings==null)throw new IllegalStateException("内容运营设置不存在");
        int crawl=dto.getCrawlIntervalMinutes()==null?240:dto.getCrawlIntervalMinutes(); int comments=dto.getCommentIntervalMinutes()==null?30:dto.getCommentIntervalMinutes();
        range(crawl,15,10080,"采集周期");range(comments,5,1440,"评论周期");
        ContentOperationsMapper.SettingsRecord record=new ContentOperationsMapper.SettingsRecord(); record.setAutomationEnabled(dto.getAutomationEnabled());
        record.setDefaultPublishMode(mode(dto.getDefaultPublishMode()));record.setCrawlIntervalMinutes(crawl);record.setCommentIntervalMinutes(comments);
        String contentModel=dto.getContentModel()==null?text(currentSettings.get("contentModel")):required(dto.getContentModel(),"内容模型",100);
        record.setContentModel(contentModel);repository.updateSettings(record);return settingsFrom(repository.findSettings());
    }

    private List<ContentAccountVO> accounts(){List<ContentAccountVO> result=new ArrayList<>();for(Map<String,Object> row:repository.findAccounts())result.add(accountFrom(row));return result;}
    private ContentAccountVO account(long id){Map<String,Object> row=repository.findAccount(id);if(row==null)throw new IllegalArgumentException("小红书账号不存在");return accountFrom(row);}
    private ContentAccountVO accountFrom(Map<String,Object> r){return new ContentAccountVO(number(r.get("id")),text(r.get("platform")),text(r.get("displayName")),text(r.get("accountHandle")),text(r.get("publishMode")),truth(r.get("enabled")),text(r.get("connectionStatus")),text(r.get("adapterStatus")),text(r.get("lastError")),time(r.get("lastPublishedAt")));}
    private List<ContentSourceVO> sources(){List<ContentSourceVO> result=new ArrayList<>();for(Map<String,Object> r:repository.findSources())result.add(new ContentSourceVO(number(r.get("id")),text(r.get("name")),text(r.get("sourceType")),text(r.get("sourceUrl")),integer(r.get("pollIntervalMinutes")),truth(r.get("enabled")),text(r.get("lastStatus")),time(r.get("lastCollectedAt"))));return result;}
    private List<ContentPublicationVO> publications(){List<ContentPublicationVO> result=new ArrayList<>();for(Map<String,Object> r:repository.findRecentPublications())result.add(new ContentPublicationVO(number(r.get("id")),text(r.get("title")),text(r.get("accountName")),text(r.get("publishMode")),text(r.get("status")),integer(r.get("attemptCount")),text(r.get("errorMessage")),time(r.get("scheduledAt"))));return result;}
    private ContentOperationSettingsVO settingsFrom(Map<String,Object> r){if(r==null)throw new IllegalStateException("内容运营设置不存在");return new ContentOperationSettingsVO(truth(r.get("automationEnabled")),text(r.get("defaultPublishMode")),integer(r.get("crawlIntervalMinutes")),integer(r.get("commentIntervalMinutes")),text(r.get("contentModel")),time(r.get("updatedAt")));}
    private String mode(String value){String v=required(value,"发布模式",20).toUpperCase(Locale.ROOT);if(!MODES.contains(v))throw new IllegalArgumentException("发布模式只能是 AUTO 或 MANUAL");return v;}
    private String required(String value,String label,int max){String v=value==null?"":value.trim();if(v.isEmpty())throw new IllegalArgumentException(label+"不能为空");if(v.length()>max)throw new IllegalArgumentException(label+"长度不能超过 "+max);return v;}
    private String optional(String value,int max){if(value==null||value.trim().isEmpty())return null;String v=value.trim();if(v.length()>max)throw new IllegalArgumentException("账号标识过长");return v;}
    private void range(int value,int min,int max,String label){if(value<min||value>max)throw new IllegalArgumentException(label+"必须在 "+min+" 到 "+max+" 分钟之间");}
    private String text(Object v){return v==null?null:String.valueOf(v);} private Long number(Object v){return v==null?null:((Number)v).longValue();}
    private int integer(Object v){return v==null?0:((Number)v).intValue();} private boolean truth(Object v){return v instanceof Boolean?(Boolean)v:v!=null&&((Number)v).intValue()!=0;}
    private LocalDateTime time(Object v){if(v instanceof LocalDateTime)return (LocalDateTime)v;if(v instanceof java.sql.Timestamp)return ((java.sql.Timestamp)v).toLocalDateTime();return null;}
}
