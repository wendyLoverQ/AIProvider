package com.aiprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;

@Component
public class GeminiContentClient {
    private final ObjectMapper json; private final RestTemplate http;
    @Autowired public GeminiContentClient(ObjectMapper json,@Value("${content-ai.connect-timeout-ms:5000}") int connectTimeout,@Value("${content-ai.read-timeout-ms:90000}") int readTimeout){this(json,rest(connectTimeout,readTimeout));}
    GeminiContentClient(ObjectMapper json,RestTemplate http){this.json=json;this.http=http;}
    public String generate(GeminiRuntimeConfig config,String systemPrompt,String userPrompt){
        return generate(config,systemPrompt,userPrompt,false);
    }
    public String generateJson(GeminiRuntimeConfig config,String systemPrompt,String userPrompt){
        return generate(config,systemPrompt,userPrompt,true,null);
    }
    public String generateDraftJson(GeminiRuntimeConfig config,String systemPrompt,String userPrompt){
        ObjectNode schema=json.createObjectNode();schema.put("type","object");ObjectNode properties=schema.putObject("properties");properties.putObject("title").put("type","string").put("maxLength",20);properties.putObject("body").put("type","string").put("maxLength",1000);properties.putObject("tags").put("type","array").put("maxItems",10).putObject("items").put("type","string").put("maxLength",30);schema.putArray("required").add("title").add("body").add("tags");schema.put("additionalProperties",false);return generate(config,systemPrompt,userPrompt,true,schema);
    }
    private String generate(GeminiRuntimeConfig config,String systemPrompt,String userPrompt,boolean jsonResponse){return generate(config,systemPrompt,userPrompt,jsonResponse,null);}
    private String generate(GeminiRuntimeConfig config,String systemPrompt,String userPrompt,boolean jsonResponse,JsonNode schema){
        ObjectNode request=json.createObjectNode();request.putObject("system_instruction").putArray("parts").addObject().put("text",systemPrompt);
        request.putArray("contents").addObject().put("role","user").putArray("parts").addObject().put("text",userPrompt);
        ObjectNode generation=request.putObject("generationConfig");generation.put("temperature",config.temperature);generation.put("maxOutputTokens",config.maxOutputTokens);
        if(jsonResponse)generation.put("responseMimeType","application/json");if(schema!=null)generation.set("responseJsonSchema",schema);
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("x-goog-api-key",config.apiKey);
        URI uri=URI.create(config.apiBaseUrl+"/v1beta/models/"+config.model+":generateContent");
        try{ResponseEntity<JsonNode> response=http.exchange(uri,HttpMethod.POST,new HttpEntity<>(request,headers),JsonNode.class);return extract(response.getBody());}
        catch(HttpStatusCodeException e){throw new ContentAiException("GEMINI_HTTP_"+e.getRawStatusCode(),"Gemini 请求失败（HTTP "+e.getRawStatusCode()+"）");}
        catch(RestClientException e){throw new ContentAiException("GEMINI_UNAVAILABLE","Gemini 服务不可用或请求超时",e);}
    }
    private String extract(JsonNode body){if(body==null)throw new ContentAiException("EMPTY_RESPONSE","Gemini 返回空响应");JsonNode parts=body.path("candidates").path(0).path("content").path("parts");StringBuilder out=new StringBuilder();if(parts.isArray())for(JsonNode part:parts)if(part.hasNonNull("text"))out.append(part.path("text").asText());String value=out.toString().trim();if(value.isEmpty()){String reason=body.path("promptFeedback").path("blockReason").asText();throw new ContentAiException("NO_TEXT",reason.isEmpty()?"Gemini 未返回可用文本":"Gemini 拒绝生成："+reason);}return value;}
    private static RestTemplate rest(int connect,int read){if(connect<100||connect>30000||read<1000||read>180000)throw new IllegalArgumentException("Gemini 超时配置不合法");SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(connect);factory.setReadTimeout(read);return new RestTemplate(factory);}
}
