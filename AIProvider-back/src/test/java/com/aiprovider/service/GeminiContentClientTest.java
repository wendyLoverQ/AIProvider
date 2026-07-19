package com.aiprovider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiContentClientTest {
    @Test void sendsApiKeyInHeaderAndParsesGeneratedText(){
        RestTemplate http=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(http);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key","secret-key-value"))
            .andExpect(jsonPath("$.system_instruction.parts[0].text").value("system prompt"))
            .andExpect(jsonPath("$.contents[0].parts[0].text").value("source content"))
            .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"生成结果\"}]}}]}", MediaType.APPLICATION_JSON));
        GeminiRuntimeConfig config=new GeminiRuntimeConfig(true,"https://generativelanguage.googleapis.com","gemini-3.5-flash","secret-key-value","relevance","rewrite","reply",new BigDecimal("0.7"),2048);
        assertEquals("生成结果",new GeminiContentClient(new ObjectMapper(),http).generate(config,"system prompt","source content"));server.verify();
    }
    @Test void requestsJsonResponseForClassification(){
        RestTemplate http=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(http);
        server.expect(anything()).andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
            .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"relevant\\\":true,\\\"score\\\":0.9,\\\"reason\\\":\\\"相关\\\"}\"}]}}]}",MediaType.APPLICATION_JSON));
        GeminiRuntimeConfig config=new GeminiRuntimeConfig(true,"https://generativelanguage.googleapis.com","model","key","relevance","rewrite","reply",BigDecimal.ZERO,512);
        assertEquals("{\"relevant\":true,\"score\":0.9,\"reason\":\"相关\"}",new GeminiContentClient(new ObjectMapper(),http).generateJson(config,"system","content"));server.verify();
    }
    @Test void constrainsDraftJsonWithTheAcceptedSchema(){
        RestTemplate http=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(http);
        server.expect(anything()).andExpect(jsonPath("$.generationConfig.responseJsonSchema.required[0]").value("title"))
            .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.title.maxLength").value(20))
            .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.body.maxLength").value(1000))
            .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.tags.maxItems").value(10))
            .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"title\\\":\\\"合规标题\\\",\\\"body\\\":\\\"正文\\\",\\\"tags\\\":[]}\"}]}}]}",MediaType.APPLICATION_JSON));
        GeminiRuntimeConfig config=new GeminiRuntimeConfig(true,"https://generativelanguage.googleapis.com","model","key","relevance","rewrite","reply",BigDecimal.ZERO,512);
        assertEquals("{\"title\":\"合规标题\",\"body\":\"正文\",\"tags\":[]}",new GeminiContentClient(new ObjectMapper(),http).generateDraftJson(config,"system","content"));server.verify();
    }
}
