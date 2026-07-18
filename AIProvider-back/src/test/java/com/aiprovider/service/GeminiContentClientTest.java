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
        GeminiRuntimeConfig config=new GeminiRuntimeConfig(true,"https://generativelanguage.googleapis.com","gemini-3.5-flash","secret-key-value","rewrite","reply",new BigDecimal("0.7"),2048);
        assertEquals("生成结果",new GeminiContentClient(new ObjectMapper(),http).generate(config,"system prompt","source content"));server.verify();
    }
}
