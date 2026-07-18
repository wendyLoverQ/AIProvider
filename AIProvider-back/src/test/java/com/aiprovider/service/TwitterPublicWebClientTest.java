package com.aiprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwitterPublicWebClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TwitterPublicWebClient client = new TwitterPublicWebClient(json, true, 1000, "");

    @Test
    void keepsOnlyRequiredCookiesFromHeader() throws Exception {
        JsonNode result = json.readTree(client.normalizeCredential("guest_id=ignored; auth_token=secret-a; ct0=secret-b; lang=zh"));
        assertEquals("secret-a", result.path("auth_token").asText());
        assertEquals("secret-b", result.path("ct0").asText());
        assertEquals(2, result.size());
    }

    @Test
    void acceptsBrowserExportJson() throws Exception {
        String exported = "[{\"name\":\"auth_token\",\"value\":\"a\",\"domain\":\".x.com\"},{\"name\":\"ct0\",\"value\":\"b\"}]";
        JsonNode result = json.readTree(client.normalizeCredential(exported));
        assertEquals("a", result.path("auth_token").asText());
        assertEquals("b", result.path("ct0").asText());
    }

    @Test
    void acceptsNetscapeCookieFile() throws Exception {
        String exported = "# Netscape HTTP Cookie File\n"
                + ".x.com\tTRUE\t/\tTRUE\t1900000000\tauth_token\tdummy-auth\n"
                + ".x.com\tTRUE\t/\tTRUE\t1900000000\tct0\tdummy-csrf\n"
                + ".x.com\tTRUE\t/\tTRUE\t1900000000\tguest_id\tignored";
        JsonNode result = json.readTree(client.normalizeCredential(exported));
        assertEquals("dummy-auth", result.path("auth_token").asText());
        assertEquals("dummy-csrf", result.path("ct0").asText());
        assertEquals(2, result.size());
    }

    @Test
    void rejectsIncompleteSessionWithoutEchoingSecret() {
        ContentSourceException error = assertThrows(ContentSourceException.class, () -> client.normalizeCredential("auth_token=do-not-echo"));
        assertTrue(error.getMessage().contains("auth_token 和 ct0"));
        assertFalse(error.getMessage().contains("do-not-echo"));
    }
}
