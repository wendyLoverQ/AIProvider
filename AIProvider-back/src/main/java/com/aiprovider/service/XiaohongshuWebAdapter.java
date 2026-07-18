package com.aiprovider.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.net.URI;

@Component
public class XiaohongshuWebAdapter {
    private static final Logger log = LoggerFactory.getLogger(XiaohongshuWebAdapter.class);
    private static final Pattern QR_CODE_STATUS = Pattern.compile("\\\"codeStatus\\\"\\s*:\\s*([0-3])");
    private static final Pattern SAFE_RESPONSE_FIELD = Pattern.compile("\\\"(codeStatus|code|result|success)\\\"\\s*:\\s*(\\\"[^\\\"]{0,80}\\\"|-?\\d+|true|false|null)");
    private final boolean headless;
    private final double timeoutMs;
    private final long loginSessionTtlMs;
    private final String executable;
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();

    public XiaohongshuWebAdapter(@Value("${xiaohongshu.headless:true}") boolean headless, @Value("${xiaohongshu.navigation-timeout-ms:60000}") long timeoutMs, @Value("${xiaohongshu.login-session-ttl-ms:300000}") long loginSessionTtlMs, @Value("${xiaohongshu.browser-executable-path:}") String executable) {
        this.headless = headless;
        this.timeoutMs = timeoutMs;
        this.loginSessionTtlMs = loginSessionTtlMs;
        this.executable = executable == null ? "" : executable.trim();
    }

    public LoginSnapshot startLogin(long accountId) {
        closeAccountSessions(accountId);
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        try {
            playwright = Playwright.create();
            browser = launch(playwright);
            context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
            context.setDefaultTimeout(timeoutMs);
            Page page = context.newPage();
            LoginSignals signals = new LoginSignals();
            page.onResponse(signals::observe);
            page.navigate("https://creator.xiaohongshu.com/login?source=official", new Page.NavigateOptions().setTimeout(timeoutMs));
            page.waitForTimeout(1200);
            openQrLogin(page);
            String image = qrImage(page);
            String id = UUID.randomUUID().toString();
            LoginSession session = new LoginSession(id, accountId, playwright, browser, context, page, signals, context.storageState());
            sessions.put(id, session);
            log.info("XHS_QR session_started accountId={} session={} page={} cookies={}", accountId, shortSession(id), safeLocation(page.url()), cookieNames(context));
            return new LoginSnapshot(id, "WAITING_SCAN", image, "请使用小红书 App 扫码登录");
        } catch (RuntimeException e) {
            log.error("XHS_QR session_start_failed accountId={} error={}", accountId, e.getClass().getSimpleName());
            closeResources(context, browser, playwright);
            if (e instanceof XiaohongshuAutomationException) throw e;
            if (e instanceof PlaywrightException)
                throw new XiaohongshuAutomationException("无法打开小红书扫码登录页：" + safe((PlaywrightException) e), e);
            throw e;
        }
    }

    public LoginSnapshot poll(long accountId, String sessionId) {
        LoginSession session = sessions.get(sessionId);
        if (session == null || session.accountId != accountId)
            throw new IllegalArgumentException("扫码登录会话不存在或已过期");
        synchronized (session) {
            if (System.currentTimeMillis() - session.createdAt > loginSessionTtlMs) {
                log.info("XHS_QR session_expired accountId={} session={} reason=ttl", accountId, shortSession(sessionId));
                sessions.remove(sessionId);
                session.close();
                return new LoginSnapshot(sessionId, "EXPIRED", null, "扫码登录会话已过期，请重新发起");
            }
            try {
                String state = session.context.storageState();
                boolean storageChanged = !session.initialStorageState.equals(state);
                boolean cookieAuthenticated = authenticated(session.context);
                boolean creatorHome = isCreatorHomeUrl(session.page.url());
                boolean protocolCompleted = session.signals.codeStatus == 2 && session.signals.loginStatusAccepted && storageChanged;
                log.info("XHS_QR poll accountId={} session={} codeStatus={} statusApiAccepted={} storageChanged={} cookieAuthenticated={} creatorHome={} page={} pageHint={} cookies={}",
                        accountId, shortSession(sessionId), session.signals.codeStatus, session.signals.loginStatusAccepted, storageChanged,
                        cookieAuthenticated, creatorHome, safeLocation(session.page.url()), visibleLoginState(session.page), cookieNames(session.context));
                if (cookieAuthenticated || creatorHome || protocolCompleted) {
                    log.info("XHS_QR connected accountId={} session={} source={} cookies={}", accountId, shortSession(sessionId),
                            cookieAuthenticated ? "cookie" : creatorHome ? "creator_home" : "protocol_and_storage", cookieNames(session.context));
                    sessions.remove(sessionId);
                    session.close();
                    return new LoginSnapshot(sessionId, "CONNECTED", null, "小红书扫码登录成功", state);
                }
                if (session.signals.codeStatus == 3) {
                    log.info("XHS_QR session_expired accountId={} session={} reason=protocol", accountId, shortSession(sessionId));
                    sessions.remove(sessionId);
                    session.close();
                    return new LoginSnapshot(sessionId, "EXPIRED", null, "二维码已失效，请重新发起扫码");
                }
                String message = session.signals.codeStatus == 1 ? "二维码已扫描，请在手机上确认登录" : session.signals.codeStatus == 2 ? "手机已确认，等待创作中心完成登录" : "等待扫码确认";
                return new LoginSnapshot(sessionId, "WAITING_SCAN", null, message);
            } catch (PlaywrightException e) {
                log.error("XHS_QR poll_failed accountId={} session={} error={}", accountId, shortSession(sessionId), e.getClass().getSimpleName());
                sessions.remove(sessionId);
                session.close();
                throw new XiaohongshuAutomationException("检查小红书登录状态失败：" + safe(e), e);
            }
        }
    }

    public String publish(String storageState, String title, String body, List<String> tags, Path card) {
        try (Playwright playwright = Playwright.create(); Browser browser = launch(playwright); BrowserContext context = browser.newContext(new Browser.NewContextOptions().setStorageState(storageState).setViewportSize(1280, 720))) {
            context.setDefaultTimeout(timeoutMs);
            Page page = context.newPage();
            page.navigate("https://creator.xiaohongshu.com/publish/publish?source=official", new Page.NavigateOptions().setTimeout(timeoutMs));
            page.waitForTimeout(1000);
            if (isLoginUrl(page.url())) throw new XiaohongshuAutomationException("小红书登录会话已过期，请重新扫码登录");
            if (!clickIfVisible(page, "上传图文")) clickIfVisible(page, "发布图文");
            Locator imageInput = page.locator("input[type='file'][accept*='image'],input[type='file']").first();
            imageInput.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            imageInput.setInputFiles(card);
            waitForUpload(page);
            Locator titleInput = page.locator("input[placeholder*='标题'],textarea[placeholder*='标题']").first();
            titleInput.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            titleInput.fill(title);
            Locator editor = page.locator("[contenteditable='true'],textarea[placeholder*='正文'],textarea[placeholder*='描述']").first();
            editor.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            String content = body + formatTags(tags);
            editor.fill(content);
            Locator publish = page.getByText("发布", new Page.GetByTextOptions().setExact(true)).last();
            publish.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            publish.click();
            long deadline = System.currentTimeMillis() + (long) timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (page.url().contains("success") || page.getByText("发布成功").count() > 0) return page.url();
                page.waitForTimeout(300);
            }
            throw new XiaohongshuAutomationException("已点击发布，但小红书未返回明确成功结果；请人工确认该任务，系统不会自动重试", true);
        } catch (XiaohongshuAutomationException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw new XiaohongshuAutomationException("小红书网页发布失败：" + safe(e), e);
        }
    }

    private boolean authenticated(BrowserContext context) {
        return context.cookies().stream().anyMatch(c -> isAuthenticatedCookieName(c.name) && !blank(c.value));
    }

    static boolean isCreatorHomeUrl(String url) {
        return url != null && (url.startsWith("https://creator.xiaohongshu.com/creator/home")
                || url.startsWith("https://creator.xiaohongshu.com/new/home"));
    }

    static boolean isAuthenticatedCookieName(String name) {
        return "web_session".equals(name)
                || "access-token".equals(name)
                || "access-token-creator.xiaohongshu.com".equals(name);
    }

    static boolean isLoginUrl(String url) {
        return url != null && url.contains("creator.xiaohongshu.com/login");
    }

    static int qrCodeStatus(String body) {
        if (body == null) return -1;
        Matcher matcher = QR_CODE_STATUS.matcher(body);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    static String safeResponseFields(String body) {
        if (body == null) return "[]";
        List<String> fields = new ArrayList<>();
        Matcher matcher = SAFE_RESPONSE_FIELD.matcher(body);
        while (matcher.find()) fields.add(matcher.group(1) + "=" + matcher.group(2));
        return fields.toString();
    }

    static SortedSet<String> setCookieNames(List<String> headers) {
        SortedSet<String> names = new TreeSet<>();
        if (headers == null) return names;
        for (String header : headers) {
            if (header == null) continue;
            int equals = header.indexOf('=');
            if (equals > 0) names.add(header.substring(0, equals).trim());
        }
        return names;
    }

    private static String shortSession(String id) {
        if (id == null || id.length() <= 8) return id;
        return id.substring(id.length() - 8);
    }

    private static String safeLocation(String value) {
        if (value == null) return "";
        try {
            URI uri = URI.create(value);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
        } catch (IllegalArgumentException ignored) {
            return "invalid-url";
        }
    }

    private static SortedSet<String> cookieNames(BrowserContext context) {
        SortedSet<String> names = new TreeSet<>();
        context.cookies().forEach(cookie -> names.add(cookie.name));
        return names;
    }

    private static String visibleLoginState(Page page) {
        String[] hints = {"扫描成功", "请在手机上确认登录", "二维码已失效，请刷新", "加载失败", "请返回重试"};
        for (String hint : hints) {
            try {
                Locator locator = page.getByText(hint, new Page.GetByTextOptions().setExact(false)).first();
                if (locator.count() > 0 && locator.isVisible()) return hint;
            } catch (PlaywrightException ignored) {
            }
        }
        return "none";
    }

    private void openQrLogin(Page page) {
        Locator heading = page.getByText("APP扫一扫登录", new Page.GetByTextOptions().setExact(true));
        if (heading.count() > 0 && heading.isVisible()) return;
        Locator switcher = page.locator(".login-box-container img[src^='data:image/png;base64']");
        if (switcher.count() != 1 || !switcher.isVisible())
            throw new XiaohongshuAutomationException("小红书登录页未找到二维码切换入口");
        switcher.click();
        heading.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
    }

    private String qrImage(Page page) {
        Locator heading = page.getByText("APP扫一扫登录", new Page.GetByTextOptions().setExact(true));
        heading.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
        Locator images = page.locator(".login-box-container img[src^='data:image/png;base64']");
        long deadline = System.currentTimeMillis() + Math.min((long) timeoutMs, 5000L);
        while (System.currentTimeMillis() < deadline) {
            Locator qr = null;
            for (int i = 0; i < images.count(); i++) {
                Locator candidate = images.nth(i);
                BoundingBox box = candidate.boundingBox();
                if (candidate.isVisible() && box != null && box.width >= 120 && box.height >= 120) {
                    if (qr != null) throw new XiaohongshuAutomationException("小红书登录页出现多个二维码候选");
                    qr = candidate;
                }
            }
            if (qr != null) {
                String source = qr.getAttribute("src");
                if (source == null || !source.startsWith("data:image/png;base64,"))
                    throw new XiaohongshuAutomationException("小红书登录二维码格式异常");
                return source;
            }
            page.waitForTimeout(100);
        }
        throw new XiaohongshuAutomationException("小红书登录页未生成可扫描二维码");
    }

    private boolean clickIfVisible(Page page, String text) {
        try {
            Locator target = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
            if (target.count() > 0 && target.isVisible()) {
                target.click();
                page.waitForTimeout(300);
                return true;
            }
        } catch (PlaywrightException ignored) {
        }
        return false;
    }

    private void waitForUpload(Page page) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean progress = page.locator("[role='progressbar'],[class*='upload'][class*='progress']").count() > 0;
            if (!progress && page.locator("img").count() > 0) return;
            page.waitForTimeout(300);
        }
        throw new XiaohongshuAutomationException("等待小红书文字卡上传完成超时");
    }

    private String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\n\n");
        for (String tag : tags)
            if (tag != null && !tag.trim().isEmpty())
                out.append('#').append(tag.trim().replaceFirst("^#+", "")).append(' ');
        return out.toString();
    }

    private Browser launch(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless).setArgs(Arrays.asList("--disable-dev-shm-usage", "--no-sandbox"));
        if (!executable.isEmpty()) options.setExecutablePath(java.nio.file.Paths.get(executable));
        return playwright.chromium().launch(options);
    }

    private void closeResources(BrowserContext context, Browser browser, Playwright playwright) {
        if (context != null) try {
            context.close();
        } catch (Exception ignored) {
        }
        if (browser != null) try {
            browser.close();
        } catch (Exception ignored) {
        }
        if (playwright != null) try {
            playwright.close();
        } catch (Exception ignored) {
        }
    }

    private void closeAccountSessions(long accountId) {
        for (LoginSession session : new ArrayList<>(sessions.values()))
            if (session.accountId == accountId && sessions.remove(session.id, session)) session.close();
    }

    @PreDestroy
    public void close() {
        for (LoginSession session : sessions.values()) session.close();
        sessions.clear();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(Exception e) {
        String v = e.getMessage();
        if (blank(v)) return e.getClass().getSimpleName();
        int line = v.indexOf('\n');
        v = line < 0 ? v : v.substring(0, line);
        return v.length() > 300 ? v.substring(0, 300) : v;
    }

    public static class LoginSnapshot {
        final String sessionId;
        final String status;
        final String image;
        final String message;
        final String storageState;

        LoginSnapshot(String id, String status, String image, String message) {
            this(id, status, image, message, null);
        }

        LoginSnapshot(String id, String status, String image, String message, String storageState) {
            this.sessionId = id;
            this.status = status;
            this.image = image;
            this.message = message;
            this.storageState = storageState;
        }
    }

    private static class LoginSignals {
        volatile int codeStatus = -1;
        volatile boolean loginStatusAccepted;

        void observe(Response response) {
            try {
                String url = response.url();
                if (url.contains("/api/qrcode/userinfo")) {
                    String body = response.text();
                    int value = qrCodeStatus(body);
                    if (value >= 0) codeStatus = value;
                    log.info("XHS_QR api endpoint=userinfo httpStatus={} ok={} fields={} setCookies={}", response.status(), response.ok(), safeResponseFields(body), responseCookieNames(response));
                } else if (url.contains("/api/sns/web/v1/login/qrcode/status")) {
                    String body = response.text();
                    if (response.ok()) loginStatusAccepted = true;
                    int value = qrCodeStatus(body);
                    if (value >= 0) codeStatus = value;
                    log.info("XHS_QR api endpoint=status httpStatus={} ok={} fields={} setCookies={}", response.status(), response.ok(), safeResponseFields(body), responseCookieNames(response));
                }
            } catch (PlaywrightException e) {
                log.warn("XHS_QR api_observe_failed error={}", e.getClass().getSimpleName());
            }
        }

        private static SortedSet<String> responseCookieNames(Response response) {
            String value = response.headers().get("set-cookie");
            return value == null ? new TreeSet<>() : setCookieNames(Collections.singletonList(value));
        }
    }

    private static class LoginSession {
        final String id;
        final long accountId;
        final long createdAt = System.currentTimeMillis();
        final Playwright playwright;
        final Browser browser;
        final BrowserContext context;
        final Page page;
        final LoginSignals signals;
        final String initialStorageState;

        LoginSession(String id, long accountId, Playwright playwright, Browser browser, BrowserContext context, Page page, LoginSignals signals, String initialStorageState) {
            this.id = id;
            this.accountId = accountId;
            this.playwright = playwright;
            this.browser = browser;
            this.context = context;
            this.page = page;
            this.signals = signals;
            this.initialStorageState = initialStorageState;
        }

        void close() {
            try {
                context.close();
            } catch (Exception ignored) {
            }
            try {
                browser.close();
            } catch (Exception ignored) {
            }
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
        }
    }
}
