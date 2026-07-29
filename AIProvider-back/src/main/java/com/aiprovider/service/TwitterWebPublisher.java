package com.aiprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * X/Twitter 网页自动化适配器。这里刻意不调用官方 API；网页结构变化时只需调整本类。
 */
@Component
public class TwitterWebPublisher {
    private final ObjectMapper json;
    private final boolean headless;
    private final double timeoutMs;
    private final String browserExecutablePath;

    public TwitterWebPublisher(ObjectMapper json,
                               @Value("${twitter.headless:true}") boolean headless,
                               @Value("${twitter.navigation-timeout-ms:60000}") long timeoutMs,
                               @Value("${twitter.browser-executable-path:}") String browserExecutablePath) {
        this.json = json;
        this.headless = headless;
        this.timeoutMs = timeoutMs;
        this.browserExecutablePath = browserExecutablePath == null ? "" : browserExecutablePath.trim();
    }

    public String login(String username, String password, String identity, String verificationCode) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterWebPublisher.login", new String[] { "username", "password", "identity", "verificationCode" }, new Object[] { username, password, identity, verificationCode });
    try (Playwright playwright = Playwright.create();
             Browser browser = launch(playwright);
             BrowserContext context = browser.newContext()) {
            context.setDefaultTimeout(timeoutMs);
            Page page = context.newPage();
            page.navigate("https://x.com/i/flow/login", new Page.NavigateOptions().setTimeout(timeoutMs));

            Locator usernameInput = page.locator("input[autocomplete='username']").first();
            usernameInput.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            usernameInput.fill(username);
            usernameInput.press("Enter");

            waitForLoginStep(page);
            if (visible(page, "input[data-testid='ocfEnterTextTextInput']") && !visible(page, "input[name='password']")) {
                if (blank(identity)) {
                    throw new TwitterAutomationException("X 要求确认邮箱、手机号或用户名，请填写 emailOrPhone 后重试");
                }
                Locator challenge = page.locator("input[data-testid='ocfEnterTextTextInput']").first();
                challenge.fill(identity);
                challenge.press("Enter");
                waitForPassword(page);
            }

            Locator passwordInput = page.locator("input[name='password']").first();
            if (!passwordInput.isVisible()) {
                throw new TwitterAutomationException("未进入密码登录步骤，账号可能触发了验证码或风控验证");
            }
            passwordInput.fill(password);
            passwordInput.press("Enter");

            waitForAuthenticationOrChallenge(page);
            if (!authenticated(context) && visible(page, "input[data-testid='ocfEnterTextTextInput']")) {
                if (blank(verificationCode)) {
                    throw new TwitterAutomationException("X 要求输入登录验证码，请填写 verificationCode 后重试");
                }
                Locator code = page.locator("input[data-testid='ocfEnterTextTextInput']").first();
                code.fill(verificationCode);
                code.press("Enter");
                waitForAuthentication(context, page, 30000);
            }
            if (!authenticated(context)) {
                throw new TwitterAutomationException("X 登录失败，可能是密码错误、验证码/CAPTCHA 或账号风控");
            }
            return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterWebPublisher.login", context.storageState());
        } catch (TwitterAutomationException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw new TwitterAutomationException("无法完成 X 网页登录：" + safeMessage(e), e);
        }
    }

    public boolean validateSession(String storageState) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterWebPublisher.validateSession", new String[] { "storageState" }, new Object[] { storageState });
    try (Playwright playwright = Playwright.create(); Browser browser = launch(playwright); BrowserContext context = browser.newContext(new Browser.NewContextOptions().setStorageState(storageState))) {
            context.setDefaultTimeout(timeoutMs); Page page=context.newPage(); page.navigate("https://x.com/home",new Page.NavigateOptions().setTimeout(timeoutMs));
            return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterWebPublisher.validateSession", authenticated(context) && !page.url().contains("/i/flow/login"));
        } catch (PlaywrightException e) { return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterWebPublisher.validateSession", false); }
    }

    public String publish(String username, String storageState, String content, List<Path> images) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterWebPublisher.publish", new String[] { "username", "storageState", "content", "images" }, new Object[] { username, storageState, content, images });
    try (Playwright playwright = Playwright.create();
             Browser browser = launch(playwright);
             BrowserContext context = browser.newContext(new Browser.NewContextOptions().setStorageState(storageState))) {
            context.setDefaultTimeout(timeoutMs);
            Page page = context.newPage();
            AtomicReference<String> tweetId = new AtomicReference<>();
            AtomicReference<String> createError = new AtomicReference<>();
            page.onResponse(response -> captureCreateTweet(response, tweetId, createError));
            page.navigate("https://x.com/compose/post", new Page.NavigateOptions().setTimeout(timeoutMs));
            if (page.url().contains("/i/flow/login") || !authenticated(context)) {
                throw new TwitterAutomationException("X 登录会话已过期，请重新连接账号", true);
            }

            Locator editor = page.locator("[data-testid='tweetTextarea_0']").first();
            editor.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            if (!blank(content)) editor.fill(content);

            if (images != null && !images.isEmpty()) {
                Locator fileInput = page.locator("input[data-testid='fileInput'], input[type='file']").first();
                fileInput.setInputFiles(images.toArray(new Path[0]));
                waitForMediaReady(page, images.size());
            }

            Locator send = page.locator("button[data-testid='tweetButton'], button[data-testid='tweetButtonInline']").first();
            send.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            send.click();
            waitForCreateTweet(page, tweetId, createError);
            if (createError.get() != null) throw new TwitterAutomationException(createError.get());
            if (tweetId.get() == null) {
                throw new TwitterAutomationException("X 未返回发布成功结果，请在账号页面确认后再决定是否重试");
            }
            return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterWebPublisher.publish", "https://x.com/" + normalizeUsername(username) + "/status/" + tweetId.get());
        } catch (TwitterAutomationException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw new TwitterAutomationException("X 网页发布失败：" + safeMessage(e), e);
        }
    }

    private Browser launch(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(Collections.singletonList("--disable-dev-shm-usage"));
        if (!browserExecutablePath.isEmpty()) options.setExecutablePath(java.nio.file.Paths.get(browserExecutablePath));
        return playwright.chromium().launch(options);
    }

    private void waitForLoginStep(Page page) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (visible(page, "input[name='password']") || visible(page, "input[data-testid='ocfEnterTextTextInput']")) return;
            page.waitForTimeout(250);
        }
        throw new TwitterAutomationException("等待 X 登录页面超时");
    }

    private void waitForPassword(Page page) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (visible(page, "input[name='password']")) return;
            page.waitForTimeout(250);
        }
        throw new TwitterAutomationException("等待 X 密码输入页面超时");
    }

    private void waitForAuthenticationOrChallenge(Page page) {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            if (authenticated(page.context()) || visible(page, "input[data-testid='ocfEnterTextTextInput']")) return;
            page.waitForTimeout(250);
        }
    }

    private void waitForAuthentication(BrowserContext context, Page page, long timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            if (authenticated(context)) return;
            page.waitForTimeout(250);
        }
    }

    private void waitForMediaReady(Page page, int count) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int previews = page.locator("[data-testid='attachments'] img, [data-testid='tweetPhoto'] img").count();
            boolean progress = page.locator("[role='progressbar']").count() > 0;
            if (previews >= count && !progress) return;
            page.waitForTimeout(300);
        }
        throw new TwitterAutomationException("等待图片上传完成超时");
    }

    private void waitForCreateTweet(Page page, AtomicReference<String> tweetId, AtomicReference<String> error) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (tweetId.get() != null || error.get() != null) return;
            page.waitForTimeout(250);
        }
    }

    private void captureCreateTweet(Response response, AtomicReference<String> tweetId, AtomicReference<String> error) {
        if (!response.url().contains("CreateTweet")) return;
        try {
            String body = response.text();
            if (!response.ok()) {
                error.compareAndSet(null, "X 拒绝发布（HTTP " + response.status() + "）");
                return;
            }
            JsonNode root = json.readTree(body);
            JsonNode id = root.at("/data/create_tweet/tweet_results/result/rest_id");
            if (id.isMissingNode()) id = root.at("/data/create_tweet/tweet_results/result/tweet/rest_id");
            if (id.isTextual()) tweetId.compareAndSet(null, id.asText());
            else if (root.path("errors").isArray() && root.path("errors").size() > 0) {
                String message = root.path("errors").get(0).path("message").asText("X 拒绝发布");
                error.compareAndSet(null, "X 拒绝发布：" + message);
            }
        } catch (Exception e) {
            error.compareAndSet(null, "无法解析 X 的发布结果");
        }
    }

    private boolean authenticated(BrowserContext context) {
        return context.cookies().stream().anyMatch(cookie -> "auth_token".equals(cookie.name) && !blank(cookie.value));
    }
    private boolean visible(Page page, String selector) {
        try { return page.locator(selector).first().isVisible(); }
        catch (PlaywrightException e) { return false; }
    }
    private String normalizeUsername(String username) { return username.startsWith("@") ? username.substring(1) : username; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String safeMessage(Exception e) {
        String value = e.getMessage();
        if (value == null || value.trim().isEmpty()) return e.getClass().getSimpleName();
        int newline = value.indexOf('\n');
        value = newline < 0 ? value : value.substring(0, newline);
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
