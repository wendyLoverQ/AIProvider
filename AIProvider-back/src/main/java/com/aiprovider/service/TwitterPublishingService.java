package com.aiprovider.service;

import com.aiprovider.mapper.TwitterMapper;
import com.aiprovider.model.dto.TwitterAccountConnectDTO;
import com.aiprovider.model.dto.TwitterPostCreateDTO;
import com.aiprovider.model.dto.TwitterClientAccountDTO;
import com.aiprovider.model.dto.TwitterClientResultDTO;
import com.aiprovider.model.vo.TwitterAccountVO;
import com.aiprovider.model.vo.TwitterMediaVO;
import com.aiprovider.model.vo.TwitterPostVO;
import com.aiprovider.repository.TwitterRepository;
import com.aiprovider.repository.AssetRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TwitterPublishingService {
    private static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    private final TwitterRepository repository;
    private final AssetRepository assetRepository;
    private final TwitterWebPublisher publisher;
    private final TwitterSessionCipher cipher;
    private final TaskExecutor executor;
    private final Path storageRoot;
    private final boolean serverPublishing;
    private final Map<Long, Object> accountLocks = new ConcurrentHashMap<>();

    public TwitterPublishingService(TwitterRepository repository, AssetRepository assetRepository, TwitterWebPublisher publisher,
                                    TwitterSessionCipher cipher,
                                    @Qualifier("twitterTaskExecutor") TaskExecutor executor,
                                    @Value("${twitter.storage-directory:/opt/aimaid/twitter-media}") String storageDirectory,
                                    @Value("${twitter.publish-mode:client}") String publishMode) {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.publisher = publisher;
        this.cipher = cipher;
        this.executor = executor;
        this.storageRoot = Paths.get(storageDirectory).toAbsolutePath().normalize();
        this.serverPublishing = "server".equalsIgnoreCase(publishMode);
    }

    public TwitterAccountVO connect(TwitterAccountConnectDTO dto) {
        if (!serverPublishing) {
            throw new IllegalArgumentException("当前为本机发布模式，请通过本机 Agent 登录 Twitter");
        }
        if (dto == null || blank(dto.getUsername()) || blank(dto.getPassword())) {
            throw new IllegalArgumentException("Twitter 用户名和密码不能为空");
        }
        String username = normalizeUsername(dto.getUsername());
        if (!username.matches("[A-Za-z0-9_]{1,50}")) throw new IllegalArgumentException("Twitter 用户名格式不合法");
        cipher.ensureConfigured();
        String state = publisher.login(username, dto.getPassword(), trim(dto.getEmailOrPhone()), trim(dto.getVerificationCode()));
        long id = repository.saveConnectedAccount(username, cipher.encrypt(state));
        return accountFrom(repository.findAccount(id));
    }

    public List<TwitterAccountVO> listAccounts() {
        List<TwitterAccountVO> result = new ArrayList<>();
        for (Map<String, Object> row : repository.findAccounts()) result.add(accountFrom(row));
        return result;
    }

    @Transactional
    public long createPost(TwitterPostCreateDTO dto) {
        validatePost(dto);
        Map<String, Object> account = repository.findAccount(dto.getAccountId());
        if (account == null) throw new IllegalArgumentException("Twitter 账号不存在");
        if (!"CONNECTED".equals(text(account.get("sessionStatus")))) {
            throw new IllegalArgumentException("Twitter 账号未连接或会话已过期");
        }

        TwitterMapper.PostInsert post = new TwitterMapper.PostInsert();
        post.setAccountId(dto.getAccountId());
        post.setContent(dto.getContent() == null ? "" : dto.getContent().trim());
        int delay = dto.getDelayMinutes() == null ? 0 : dto.getDelayMinutes();
        post.setScheduledAt(LocalDateTime.now().plusMinutes(delay));
        post.setSource(dto.getDelayMinutes() == null ? "MANUAL" : "GALLERY");
        long postId = repository.insertPost(post);
        Path postDirectory = storageRoot.resolve(String.valueOf(postId)).normalize();
        ensureWithinRoot(postDirectory);
        try {
            Files.createDirectories(postDirectory);
            List<MultipartFile> images = nonEmpty(dto.getImages());
            List<Long> assetIds = normalizedAssetIds(dto, images.size());
            List<TwitterMapper.MediaInsert> media = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) media.add(prepareImage(postId, postDirectory, images.get(i), assetIds.get(i), i));
            if (!media.isEmpty()) repository.insertMediaBatch(media);
            cleanupStorageOnRollback(postDirectory);
        } catch (RuntimeException e) {
            deleteQuietly(postDirectory);
            throw e;
        } catch (IOException e) {
            deleteQuietly(postDirectory);
            throw new IllegalStateException("保存 Twitter 图片失败", e);
        }
        if (serverPublishing && delay == 0) enqueueAfterCommit(postId);
        return postId;
    }

    public TwitterPostVO getPost(long id) {
        Map<String, Object> row = repository.findPost(id);
        if (row == null) throw new IllegalArgumentException("Twitter 发布记录不存在");
        return postFrom(row);
    }

    public List<TwitterPostVO> listPosts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<TwitterPostVO> result = new ArrayList<>();
        for (Map<String, Object> row : repository.findPosts(safeLimit)) result.add(postFrom(row));
        return result;
    }

    @Transactional
    public TwitterAccountVO registerClientAccount(TwitterClientAccountDTO dto) {
        if (dto == null || blank(dto.getUsername())) throw new IllegalArgumentException("Twitter 用户名不能为空");
        String username = normalizeUsername(dto.getUsername());
        if (!username.matches("[A-Za-z0-9_]{1,50}")) throw new IllegalArgumentException("Twitter 用户名格式不合法");
        String status = "CONNECTED".equalsIgnoreCase(dto.getStatus()) ? "CONNECTED" : "DISCONNECTED";
        long id = repository.saveClientAccount(username, status);
        return accountFrom(repository.findAccount(id));
    }

    public List<TwitterPostVO> pendingPosts(long accountId, int limit) {
        repository.recoverStaleClientPosts();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<TwitterPostVO> result = new ArrayList<>();
        for (Map<String, Object> row : repository.findPendingPosts(accountId, safeLimit)) result.add(postFrom(row));
        return result;
    }

    @Transactional
    public TwitterPostVO claimForClient(long id) {
        if (!repository.claimPost(id)) throw new IllegalArgumentException("任务不存在、已被领取或当前不可发布");
        return getPost(id);
    }

    @Transactional
    public void completeFromClient(long id, TwitterClientResultDTO dto) {
        if (dto == null) throw new IllegalArgumentException("发布结果不能为空");
        if (dto.isSuccess()) {
            if (!repository.markPostSent(id, limit(trim(dto.getTweetUrl()), 500)))
                throw new IllegalArgumentException("任务不在发布中状态");
        } else {
            String error = blank(dto.getErrorMessage()) ? "本机发布失败" : limit(dto.getErrorMessage().trim(), 2000);
            if (!repository.markPostFailed(id, error)) throw new IllegalArgumentException("任务不在发布中状态");
        }
    }

    @Transactional
    public void retry(long id) {
        Map<String, Object> post = repository.findPost(id);
        if (post == null) throw new IllegalArgumentException("Twitter 发布记录不存在");
        if (!repository.retryPost(id)) throw new IllegalArgumentException("只有 FAILED 状态的记录可以重试");
        if (serverPublishing) enqueueAfterCommit(id);
    }

    @Transactional
    public void cancel(long id) {
        if (repository.findPost(id) == null) throw new IllegalArgumentException("Twitter 发布记录不存在");
        if (!repository.cancelPost(id)) throw new IllegalArgumentException("只有等待中或失败的任务可以取消");
    }

    public Resource getImage(long postId, long imageId) {
        Map<String, Object> media = repository.findMediaItem(postId, imageId);
        if (media == null) throw new IllegalArgumentException("Twitter 图片记录不存在");
        if (!blank(text(media.get("localPath")))) throw new IllegalArgumentException("本机图片必须由 Chrome 扩展从本机 Agent 读取");
        Path path = resolveStoredPath(text(media.get("storagePath")));
        Resource resource = new FileSystemResource(path.toFile());
        if (!resource.exists()) throw new IllegalArgumentException("Twitter 图片文件不存在");
        return resource;
    }

    public String getImageContentType(long postId, long imageId) {
        Map<String, Object> media = repository.findMediaItem(postId, imageId);
        if (media == null) throw new IllegalArgumentException("Twitter 图片记录不存在");
        return text(media.get("contentType"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverQueue() {
        repository.recoverProcessingPosts();
        if (!serverPublishing) return;
        for (Long postId : repository.findPendingPostIds()) enqueue(postId);
    }

    private void enqueueAfterCommit(long postId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { enqueue(postId); }
            });
        } else enqueue(postId);
    }

    private void cleanupStorageOnRollback(Path postDirectory) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(postDirectory);
            }
        });
    }

    private void enqueue(long postId) { executor.execute(() -> publish(postId)); }

    private void publish(long postId) {
        Map<String, Object> post = repository.findPost(postId);
        if (post == null) return;
        long accountId = number(post.get("accountId"));
        Object lock = accountLocks.computeIfAbsent(accountId, ignored -> new Object());
        synchronized (lock) {
            if (!repository.claimPost(postId)) return;
            try {
                Map<String, Object> account = repository.findAccount(accountId);
                if (account == null || blank(text(account.get("encryptedStorageState")))) {
                    throw new TwitterAutomationException("Twitter 账号没有可用的登录会话", true);
                }
                String state = cipher.decrypt(text(account.get("encryptedStorageState")));
                List<Path> paths = new ArrayList<>();
                for (Map<String, Object> media : repository.findMedia(postId)) {
                    Path path = resolveStoredPath(text(media.get("storagePath")));
                    if (!Files.isRegularFile(path)) throw new TwitterAutomationException("服务器图片文件不存在：" + media.get("originalFileName"));
                    paths.add(path);
                }
                String url = publisher.publish(text(account.get("username")), state, text(post.get("content")), paths);
                repository.markPostSent(postId, url);
                repository.updateAccountStatus(accountId, "CONNECTED", null);
            } catch (TwitterAutomationException e) {
                String error = limit(e.getMessage(), 2000);
                repository.markPostFailed(postId, error);
                if (e.isSessionExpired()) repository.updateAccountStatus(accountId, "EXPIRED", limit(error, 1000));
            } catch (RuntimeException e) {
                repository.markPostFailed(postId, limit("发布任务异常：" + e.getMessage(), 2000));
            }
        }
    }

    private void validatePost(TwitterPostCreateDTO dto) {
        if (dto == null || dto.getAccountId() == null || dto.getAccountId() <= 0) throw new IllegalArgumentException("accountId 不能为空");
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        List<MultipartFile> images = nonEmpty(dto.getImages());
        if (content.isEmpty() && images.isEmpty()) throw new IllegalArgumentException("文字和图片不能同时为空");
        if (content.codePointCount(0, content.length()) > 1000) throw new IllegalArgumentException("文字不能超过 1000 个字符");
        if (images.size() > 4) throw new IllegalArgumentException("一次最多上传 4 张图片");
        if (dto.getDelayMinutes() != null && !Arrays.asList(1, 5, 10, 15, 30).contains(dto.getDelayMinutes()))
            throw new IllegalArgumentException("计划时间只能选择 1、5、10、15 或 30 分钟");
        List<Long> referencedAssetIds = normalizedAssetIds(dto, images.size()).stream().filter(Objects::nonNull).distinct().toList();
        if (!referencedAssetIds.isEmpty()) {
            Set<Long> existingIds = new HashSet<>(assetRepository.findExistingIds(referencedAssetIds));
            if (!existingIds.containsAll(referencedAssetIds)) throw new IllegalArgumentException("批量图片包含不存在的来源资产");
        }
        int gifs = 0;
        for (MultipartFile image : images) {
            if (image.getSize() <= 0 || image.getSize() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("单张图片大小必须在 15MB 以内");
            String type = detectImageType(image);
            if ("image/gif".equals(type)) gifs++;
        }
        if (gifs > 0 && images.size() > 1) throw new IllegalArgumentException("GIF 不能与其他图片同时发布");
    }

    private List<Long> normalizedAssetIds(TwitterPostCreateDTO dto, int imageCount) {
        List<Long> supplied = dto.getAssetIds() == null ? Collections.emptyList() : dto.getAssetIds();
        if (!supplied.isEmpty() && supplied.size() != imageCount) throw new IllegalArgumentException("assetIds 必须与图片一一对应");
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            Long value = supplied.isEmpty() ? null : supplied.get(i);
            result.add(value == null || value <= 0 ? null : value);
        }
        return result;
    }

    private TwitterMapper.MediaInsert prepareImage(long postId, Path directory, MultipartFile image, Long assetId, int order) throws IOException {
        String type = detectImageType(image);
        String extension = extension(type);
        Path target = directory.resolve(UUID.randomUUID().toString() + extension).normalize();
        ensureWithinRoot(target);
        image.transferTo(target);
        TwitterMapper.MediaInsert media = new TwitterMapper.MediaInsert();
        media.setPostId(postId);
        media.setAssetId(assetId);
        media.setStoragePath(storageRoot.relativize(target).toString().replace('\\', '/'));
        media.setOriginalFileName(limit(safeFileName(image.getOriginalFilename()), 255));
        media.setContentType(type);
        media.setFileSize(Files.size(target));
        media.setSha256(sha256(target));
        media.setSortOrder(order);
        return media;
    }

    private String detectImageType(MultipartFile image) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = image.getInputStream()) { read = input.read(header); }
        catch (IOException e) { throw new IllegalArgumentException("无法读取上传图片", e); }
        if (read >= 8 && header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47) return "image/png";
        if (read >= 3 && header[0] == (byte) 0xff && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff) return "image/jpeg";
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "image/webp";
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') return "image/gif";
        throw new IllegalArgumentException("仅支持真实的 PNG、JPEG、WEBP 或 GIF 图片");
    }

    private TwitterAccountVO accountFrom(Map<String, Object> row) {
        return new TwitterAccountVO(number(row.get("id")), text(row.get("username")), text(row.get("sessionStatus")),
                time(row.get("lastLoginAt")), text(row.get("lastError")));
    }

    private TwitterPostVO postFrom(Map<String, Object> row) {
        long id = number(row.get("id"));
        List<TwitterMediaVO> media = new ArrayList<>();
        for (Map<String, Object> item : repository.findMedia(id)) {
            media.add(new TwitterMediaVO(number(item.get("id")), nullableLong(item.get("assetId")), text(item.get("originalFileName")), text(item.get("contentType")),
                    number(item.get("fileSize")), integer(item.get("sortOrder")), text(item.get("localPath")), text(item.get("localSource"))));
        }
        return new TwitterPostVO(id, number(row.get("accountId")), text(row.get("username")), text(row.get("content")),
                text(row.get("status")), text(row.get("tweetUrl")), text(row.get("errorMessage")), integer(row.get("attemptCount")),
                time(row.get("sentAt")), time(row.get("createdAt")), time(row.get("scheduledAt")), text(row.get("source")), media);
    }

    private List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        if (files == null) return Collections.emptyList();
        List<MultipartFile> result = new ArrayList<>();
        for (MultipartFile file : files) if (file != null && !file.isEmpty()) result.add(file);
        return result;
    }
    private Path resolveStoredPath(String relative) {
        Path path = storageRoot.resolve(relative).normalize();
        ensureWithinRoot(path);
        return path;
    }
    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(storageRoot)) throw new IllegalArgumentException("图片存储路径不合法");
    }
    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }
    private void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }
    private String safeFileName(String value) {
        if (blank(value)) return "image";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        name = name.replaceAll("[\\r\\n\\x00-\\x1f]", "_").trim();
        return name.isEmpty() ? "image" : name;
    }
    private String normalizeUsername(String value) { String result = value.trim(); return result.startsWith("@") ? result.substring(1) : result; }
    private String extension(String type) { if ("image/png".equals(type)) return ".png"; if ("image/webp".equals(type)) return ".webp"; if ("image/gif".equals(type)) return ".gif"; return ".jpg"; }
    private Long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private Long nullableLong(Object value) { return value == null ? null : number(value); }
    private Integer integer(Object value) { return value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value)); }
    private LocalDateTime time(Object value) { if (value == null) return null; if (value instanceof LocalDateTime) return (LocalDateTime) value; if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime(); return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String limit(String value, int max) { if (value == null) return null; return value.length() <= max ? value : value.substring(0, max); }
}
