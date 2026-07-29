package com.aiprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class TwitterTimelineClient {
    private final RestTemplate http;
    @Autowired public TwitterTimelineClient(@Value("${content-platform.connect-timeout-ms:5000}") int connectTimeout,@Value("${content-platform.read-timeout-ms:30000}") int readTimeout){this(rest(connectTimeout,readTimeout));}
    TwitterTimelineClient(RestTemplate http){this.http=http;}
    public List<TwitterFetchedPost> fetch(String uid,String bearerToken,int limit){
    com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterTimelineClient.fetch", new String[] { "uid", "bearerToken", "limit" }, new Object[] { uid, bearerToken, limit });
    URI uri=UriComponentsBuilder.fromHttpUrl("https://api.x.com/2/users/"+uid+"/tweets").queryParam("max_results",limit).queryParam("tweet.fields","created_at,lang,public_metrics").queryParam("exclude","retweets,replies").build().encode().toUri();
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(bearerToken);headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        try{ResponseEntity<JsonNode> response=http.exchange(uri,HttpMethod.GET,new HttpEntity<Void>(headers),JsonNode.class);return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterTimelineClient.fetch", parse(response.getBody()));}
        catch(HttpStatusCodeException e){throw new ContentSourceException("TWITTER_HTTP_"+e.getRawStatusCode(),"Twitter 拉取失败（HTTP "+e.getRawStatusCode()+"）");}
        catch(RestClientException e){throw new ContentSourceException("TWITTER_UNAVAILABLE","Twitter API 不可用或请求超时",e);}
    }
    private List<TwitterFetchedPost> parse(JsonNode body){if(body==null)throw new ContentSourceException("EMPTY_RESPONSE","Twitter 返回空响应");JsonNode data=body.path("data");List<TwitterFetchedPost> result=new ArrayList<>();if(data.isMissingNode()||data.isNull())return result;if(!data.isArray())throw new ContentSourceException("INVALID_RESPONSE","Twitter 返回的数据格式不正确");for(JsonNode item:data){String id=item.path("id").asText();String text=item.path("text").asText();if(id.isEmpty()||text.isEmpty())continue;LocalDateTime published=null;String created=item.path("created_at").asText();if(!created.isEmpty())try{published=OffsetDateTime.parse(created).toLocalDateTime();}catch(Exception ignored){}result.add(new TwitterFetchedPost(id,text,"https://x.com/i/web/status/"+id,published,item));}return result;}
    private static RestTemplate rest(int connect,int read){if(connect<100||connect>30000||read<1000||read>120000)throw new IllegalArgumentException("内容平台超时配置不合法");SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(connect);factory.setReadTimeout(read);return new RestTemplate(factory);}
}
