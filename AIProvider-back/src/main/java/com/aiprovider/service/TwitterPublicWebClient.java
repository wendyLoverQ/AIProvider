package com.aiprovider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TwitterPublicWebClient {
    private static final Pattern STATUS = Pattern.compile("/status/([0-9]+)");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final ObjectMapper json;
    private final boolean headless;
    private final double timeoutMs;
    private final String executable;

    public TwitterPublicWebClient(
            ObjectMapper json,
            @Value("${twitter-source.headless:true}") boolean headless,
            @Value("${twitter-source.navigation-timeout-ms:60000}") long timeoutMs,
            @Value("${twitter-source.browser-executable-path:}") String executable) {
        this.json = json;
        this.headless = headless;
        this.timeoutMs = timeoutMs;
        this.executable = executable == null ? "" : executable.trim();
    }

    public String normalizeCredential(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new ContentSourceException("TWITTER_SESSION_MISSING", "X Cookie 不能为空");
        Map<String, String> cookies = new LinkedHashMap<>();
        try {
            if (value.startsWith("[") || value.startsWith("{")) readJsonCookies(json.readTree(value), cookies);
            else if (value.startsWith("# Netscape HTTP Cookie File") || value.contains("\t")) readNetscapeCookies(value, cookies);
            else readCookieHeader(value, cookies);
        } catch (ContentSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new ContentSourceException("TWITTER_SESSION_INVALID", "X Cookie 格式无法识别，请粘贴 auth_token 和 ct0", e);
        }
        requireSessionCookie(cookies, "auth_token");
        requireSessionCookie(cookies, "ct0");
        ObjectNode normalized = json.createObjectNode();
        normalized.put("auth_token", cookies.get("auth_token"));
        normalized.put("ct0", cookies.get("ct0"));
        return normalized.toString();
    }

    public TwitterFetchedPost fetchLatest(String handle, String credential) {
        try (Playwright playwright = Playwright.create();
             Browser browser = launch(playwright);
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setViewportSize(1280, 720)
                     .setLocale("zh-CN")
                     .setUserAgent(USER_AGENT))) {
            context.setDefaultTimeout(timeoutMs);
            addSessionCookies(context, credential);
            Page page = context.newPage();
            page.navigate("https://x.com/" + handle, new Page.NavigateOptions().setTimeout(timeoutMs));
            page.waitForTimeout(1500);
            if (page.url().contains("/i/flow/login")) {
                throw new ContentSourceException("TWITTER_SESSION_EXPIRED", "X Cookie 已失效，请重新导入 auth_token 和 ct0");
            }

            Locator articles = page.locator("article[data-testid='tweet']");
            articles.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            Selection selection = latestAuthoredText(articles, handle);
            Locator article = selection.article;
            String id = selection.id;
            String text = selection.text;

            LocalDateTime publishedAt = publishedAt(article);
            ObjectNode raw = json.createObjectNode();
            raw.put("adapter", "TWITTER_WEB");
            raw.put("authenticatedSession", true);
            raw.put("handle", handle);
            raw.put("id", id);
            raw.put("text", text);
            raw.put("pageUrl", page.url());
            return new TwitterFetchedPost(id, text, "https://x.com/" + handle + "/status/" + id, publishedAt, raw);
        } catch (ContentSourceException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw new ContentSourceException("TWITTER_WEB_UNAVAILABLE", "X 登录会话采集失败：" + safe(e), e);
        }
    }

    private Selection latestAuthoredText(Locator articles, String handle) {
        String expectedPrefix = "/" + handle.toLowerCase(Locale.ROOT) + "/status/";
        int count = Math.min(articles.count(), 20);
        for (int index = 0; index < count; index++) {
            Locator article = articles.nth(index);
            Locator socialContext = article.locator("[data-testid='socialContext']");
            String context = socialContext.count() == 0 ? "" : socialContext.first().innerText();
            String lowered = context.toLowerCase(Locale.ROOT);
            if (context.contains("置顶") || lowered.contains("pinned") || context.contains("转发") || lowered.contains("reposted")) continue;
            String id = null;
            Locator links = article.locator("a[href*='/status/']");
            for (int linkIndex = 0; linkIndex < links.count(); linkIndex++) {
                String href = links.nth(linkIndex).getAttribute("href");
                if (href == null || !href.toLowerCase(Locale.ROOT).startsWith(expectedPrefix)) continue;
                Matcher matcher = STATUS.matcher(href);
                if (matcher.find()) { id = matcher.group(1); break; }
            }
            if (id == null) continue;
            Locator textNode = article.locator("[data-testid='tweetText']").first();
            String text = textNode.count() > 0 ? textNode.innerText().trim() : "";
            if (!text.isEmpty()) return new Selection(article, id, text);
        }
        throw new ContentSourceException("TWITTER_LATEST_POST_MISSING", "X 时间线没有返回该账号最新的非置顶文字推文");
    }

    private void addSessionCookies(BrowserContext context, String credential) {
        String normalized = normalizeCredential(credential);
        try {
            JsonNode node = json.readTree(normalized);
            List<Cookie> cookies = new ArrayList<>();
            for (String name : Arrays.asList("auth_token", "ct0")) {
                cookies.add(new Cookie(name, node.path(name).asText())
                        .setDomain(".x.com").setPath("/").setSecure(true).setHttpOnly("auth_token".equals(name))
                        .setSameSite(SameSiteAttribute.LAX));
            }
            context.addCookies(cookies);
        } catch (ContentSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new ContentSourceException("TWITTER_SESSION_INVALID", "X Cookie 无法载入浏览器会话", e);
        }
    }

    private void readJsonCookies(JsonNode root, Map<String, String> result) {
        JsonNode cookies = root.isArray() ? root : root.path("cookies");
        if (cookies.isArray()) {
            for (JsonNode cookie : cookies) putCookie(result, cookie.path("name").asText(), cookie.path("value").asText());
            return;
        }
        if (root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) { Map.Entry<String, JsonNode> field = fields.next(); putCookie(result, field.getKey(), field.getValue().asText()); }
        }
    }

    private void readCookieHeader(String value, Map<String, String> result) {
        for (String part : value.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            putCookie(result, part.substring(0, equals).trim(), part.substring(equals + 1).trim());
        }
    }

    private void readNetscapeCookies(String value, Map<String, String> result) {
        for (String rawLine : value.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) continue;
            String[] fields = line.split("\\t", 7);
            if (fields.length != 7) continue;
            putCookie(result, fields[5].trim(), fields[6].trim());
        }
    }

    private void putCookie(Map<String, String> result, String name, String value) {
        if (("auth_token".equals(name) || "ct0".equals(name)) && value != null && !value.trim().isEmpty()) result.put(name, value.trim());
    }

    private void requireSessionCookie(Map<String, String> cookies, String name) {
        if (!cookies.containsKey(name)) throw new ContentSourceException("TWITTER_SESSION_INVALID", "X Cookie 必须同时包含 auth_token 和 ct0");
    }

    private static final class Selection {
        private final Locator article; private final String id; private final String text;
        private Selection(Locator article, String id, String text) { this.article = article; this.id = id; this.text = text; }
    }

    private LocalDateTime publishedAt(Locator article) {
        Locator time = article.locator("time").first();
        if (time.count() == 0) return null;
        try {
            return OffsetDateTime.parse(time.getAttribute("datetime")).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Browser launch(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(Arrays.asList("--disable-dev-shm-usage", "--no-sandbox"));
        if (!executable.isEmpty()) options.setExecutablePath(java.nio.file.Paths.get(executable));
        return playwright.chromium().launch(options);
    }

    private String safe(Exception e) {
        String value = e.getMessage();
        if (value == null || value.trim().isEmpty()) return e.getClass().getSimpleName();
        int line = value.indexOf('\n');
        value = line < 0 ? value : value.substring(0, line);
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
