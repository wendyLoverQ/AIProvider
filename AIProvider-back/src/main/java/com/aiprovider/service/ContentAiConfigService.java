package com.aiprovider.service;

import com.aiprovider.mapper.ContentAiMapper;
import com.aiprovider.model.dto.ContentAiConfigDTO;
import com.aiprovider.model.vo.ContentAiConfigVO;
import com.aiprovider.repository.ContentAiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ContentAiConfigService {
    private static final Pattern MODEL=Pattern.compile("^[A-Za-z0-9._-]{1,100}$");
    private final ContentAiRepository repository; private final ContentAiSecretCipher cipher;
    public ContentAiConfigService(ContentAiRepository repository,ContentAiSecretCipher cipher){this.repository=repository;this.cipher=cipher;}
    public ContentAiConfigVO get(){return toVO(requiredConfig());}

    @Transactional
    public ContentAiConfigVO save(ContentAiConfigDTO dto){
        if(dto==null||dto.getEnabled()==null)throw new IllegalArgumentException("Gemini 启用状态不能为空");
        Map<String,Object> current=requiredConfig();String newKey=trim(dto.getApiKey());String encrypted=text(current.get("apiKeyEncrypted"));String hint=text(current.get("apiKeyHint"));
        if(newKey!=null){if(newKey.length()<20||newKey.length()>500)throw new IllegalArgumentException("Gemini API Key 格式不正确");encrypted=cipher.encrypt(newKey);hint=keyHint(newKey);}
        if(dto.getEnabled()&&empty(encrypted))throw new IllegalArgumentException("启用 Gemini 前必须配置 API Key");
        ContentAiMapper.ConfigRecord record=new ContentAiMapper.ConfigRecord();record.setEnabled(dto.getEnabled());record.setApiBaseUrl(apiBaseUrl(dto.getApiBaseUrl()));
        record.setModel(model(dto.getModel()));record.setApiKeyEncrypted(encrypted);record.setApiKeyHint(hint);
        record.setContentRewritePrompt(prompt(dto.getContentRewritePrompt(),"内容改写提示词"));record.setCommentReplyPrompt(prompt(dto.getCommentReplyPrompt(),"评论回复提示词"));
        BigDecimal temperature=dto.getTemperature()==null?new BigDecimal("0.700"):dto.getTemperature();if(temperature.compareTo(BigDecimal.ZERO)<0||temperature.compareTo(new BigDecimal("2"))>0)throw new IllegalArgumentException("生成温度必须在 0 到 2 之间");record.setTemperature(temperature);
        int maxTokens=dto.getMaxOutputTokens()==null?2048:dto.getMaxOutputTokens();if(maxTokens<128||maxTokens>65536)throw new IllegalArgumentException("最大输出 Token 必须在 128 到 65536 之间");record.setMaxOutputTokens(maxTokens);
        repository.updateConfig(record);return get();
    }

    GeminiRuntimeConfig runtime(){Map<String,Object> row=requiredConfig();if(!truth(row.get("enabled")))throw new ContentAiException("AI_DISABLED","Gemini 内容生成尚未启用");String encrypted=text(row.get("apiKeyEncrypted"));if(empty(encrypted))throw new ContentAiException("API_KEY_MISSING","Gemini API Key 尚未配置");return new GeminiRuntimeConfig(true,text(row.get("apiBaseUrl")),text(row.get("model")),cipher.decrypt(encrypted),text(row.get("contentRewritePrompt")),text(row.get("commentReplyPrompt")),decimal(row.get("temperature")),integer(row.get("maxOutputTokens")));}
    private Map<String,Object> requiredConfig(){Map<String,Object> row=repository.findConfig();if(row==null)throw new IllegalStateException("Gemini 内容生成配置不存在");return row;}
    private ContentAiConfigVO toVO(Map<String,Object> r){String encrypted=text(r.get("apiKeyEncrypted"));return new ContentAiConfigVO("GEMINI",truth(r.get("enabled")),!empty(encrypted),text(r.get("apiKeyHint")),text(r.get("apiBaseUrl")),text(r.get("model")),text(r.get("contentRewritePrompt")),text(r.get("commentReplyPrompt")),decimal(r.get("temperature")),integer(r.get("maxOutputTokens")),time(r.get("updatedAt")));}
    private String apiBaseUrl(String value){String v=required(value,"Gemini API 地址",255).replaceAll("/+$","");URI uri;try{uri=URI.create(v);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Gemini API 地址格式不正确");}if(!"https".equalsIgnoreCase(uri.getScheme())||!"generativelanguage.googleapis.com".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(uri.getPath()!=null&&!uri.getPath().isEmpty()))throw new IllegalArgumentException("Gemini API 地址必须是 https://generativelanguage.googleapis.com");return v;}
    private String model(String value){String v=required(value,"Gemini 模型",100);if(!MODEL.matcher(v).matches())throw new IllegalArgumentException("Gemini 模型名称格式不正确");return v;}
    private String prompt(String value,String label){String v=required(value,label,12000);if(v.length()<20)throw new IllegalArgumentException(label+"至少需要 20 个字符");return v;}
    private String required(String value,String label,int max){String v=trim(value);if(v==null)throw new IllegalArgumentException(label+"不能为空");if(v.length()>max)throw new IllegalArgumentException(label+"长度不能超过 "+max);return v;}
    private String trim(String v){return v==null||v.trim().isEmpty()?null:v.trim();} private boolean empty(String v){return v==null||v.isEmpty();}
    private String keyHint(String key){return "••••"+key.substring(Math.max(0,key.length()-4));} private String text(Object v){return v==null?null:String.valueOf(v);}
    private boolean truth(Object v){return v instanceof Boolean?(Boolean)v:v!=null&&((Number)v).intValue()!=0;} private int integer(Object v){return v==null?0:((Number)v).intValue();}
    private BigDecimal decimal(Object v){return v instanceof BigDecimal?(BigDecimal)v:new BigDecimal(String.valueOf(v));}
    private LocalDateTime time(Object v){if(v instanceof LocalDateTime)return (LocalDateTime)v;if(v instanceof java.sql.Timestamp)return ((java.sql.Timestamp)v).toLocalDateTime();return null;}
}
