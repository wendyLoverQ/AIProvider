package com.aiprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Path;

@Component
public class AsrProviderClient {
    private final ObjectMapper json;private final RestTemplate http;private final String endpoint;private final String apiKey;
    @Autowired public AsrProviderClient(ObjectMapper json,@Value("${asr.endpoint}") String endpoint,@Value("${asr.api-key:}") String apiKey,@Value("${asr.connect-timeout-ms:5000}") int connect,@Value("${asr.read-timeout-ms:120000}") int read){this(json,rest(connect,read),endpoint,apiKey);}
    AsrProviderClient(ObjectMapper json,RestTemplate http,String endpoint,String apiKey){this.json=json;this.http=http;this.endpoint=endpoint;this.apiKey=apiKey;}
    public Result transcribe(Path audio,String model,String language){
    com.aiprovider.logging.BusinessOperationLogger.start("service.AsrProviderClient.transcribe", new String[] { "audio", "model", "language" }, new Object[] { audio, model, language });
    if(apiKey==null||apiKey.trim().isEmpty())throw new AsrProviderException("ASR_NOT_CONFIGURED","ASR_API_KEY is empty",null);
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(apiKey.trim());headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String,Object> body=new LinkedMultiValueMap<>();body.add("file",new FileSystemResource(audio.toFile()));body.add("model",model);body.add("language",language);body.add("response_format","verbose_json");
        try{ResponseEntity<String> response=http.exchange(endpoint,HttpMethod.POST,new HttpEntity<>(body,headers),String.class);JsonNode node=json.readTree(response.getBody());String text=node.path("text").asText("").trim();if(text.isEmpty())throw new AsrProviderException("ASR_EMPTY_TEXT","provider returned no text",null);HttpHeaders responseHeaders=response.getHeaders();return com.aiprovider.logging.BusinessOperationLogger.success("service.AsrProviderClient.transcribe", new Result(text,durationMs(node),headerLong(responseHeaders,"x-ratelimit-limit-requests"),headerLong(responseHeaders,"x-ratelimit-remaining-requests"),responseHeaders.getFirst("x-ratelimit-reset-requests")));}
        catch(HttpStatusCodeException e){throw new AsrProviderException("ASR_PROVIDER_HTTP_"+e.getRawStatusCode(),"upstream="+e.getResponseBodyAsString(),e);}
        catch(AsrProviderException e){throw e;}
        catch(RestClientException e){throw new AsrProviderException("ASR_PROVIDER_UNAVAILABLE",e.getMessage(),e);}
        catch(Exception e){throw new AsrProviderException("ASR_PROVIDER_RESPONSE_INVALID",e.getMessage(),e);}
    }
    private static RestTemplate rest(int connect,int read){if(connect<100||connect>30000||read<1000||read>300000)throw new IllegalArgumentException("ASR 超时配置不合法");SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(connect);factory.setReadTimeout(read);return new RestTemplate(factory);}
    private Long durationMs(JsonNode node){if(node.hasNonNull("duration"))return Math.round(node.path("duration").asDouble()*1000d);double end=-1d;JsonNode segments=node.path("segments");if(segments.isArray())for(JsonNode segment:segments)if(segment.hasNonNull("end"))end=Math.max(end,segment.path("end").asDouble());return end<0d?null:Math.round(end*1000d);}
    private Long headerLong(HttpHeaders headers,String name){String value=headers.getFirst(name);if(value==null)return null;try{return Long.parseLong(value.trim());}catch(NumberFormatException ignored){return null;}}
    public static class Result {private final String text;private final Long durationMs;private final Long requestLimit;private final Long requestsRemaining;private final String requestsResetAfter;public Result(String text,Long durationMs){this(text,durationMs,null,null,null);}public Result(String text,Long durationMs,Long requestLimit,Long requestsRemaining,String requestsResetAfter){this.text=text;this.durationMs=durationMs;this.requestLimit=requestLimit;this.requestsRemaining=requestsRemaining;this.requestsResetAfter=requestsResetAfter;}public String getText(){return text;}public Long getDurationMs(){return durationMs;}public Long getRequestLimit(){return requestLimit;}public Long getRequestsRemaining(){return requestsRemaining;}public String getRequestsResetAfter(){return requestsResetAfter;}}
}
