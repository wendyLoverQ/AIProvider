package com.aiprovider.service;

import com.aiprovider.model.vo.PlatformLoginSessionVO;import com.aiprovider.repository.PlatformAccountRepository;
import org.springframework.stereotype.Service;import java.util.Map;

@Service
public class PlatformAccountLoginService {
    private final PlatformAccountRepository repository;private final PlatformAccountService accounts;private final XiaohongshuWebAdapter xhs;private final DouyinWebAdapter douyin;
    public PlatformAccountLoginService(PlatformAccountRepository repository,PlatformAccountService accounts,XiaohongshuWebAdapter xhs,DouyinWebAdapter douyin){this.repository=repository;this.accounts=accounts;this.xhs=xhs;this.douyin=douyin;}
    public PlatformLoginSessionVO start(long accountId){
        com.aiprovider.logging.BusinessOperationLogger.start("service.PlatformAccountLoginService.start", new String[] { "accountId" }, new Object[] { accountId });
        String platform=platform(accountId);if("XIAOHONGSHU".equals(platform)){XiaohongshuWebAdapter.LoginSnapshot s=xhs.startLogin(accountId);if("CONNECTED".equals(s.status))store(accountId,platform,s.storageState);return com.aiprovider.logging.BusinessOperationLogger.success("service.PlatformAccountLoginService.start", vo(s));}if("DOUYIN".equals(platform)){DouyinWebAdapter.LoginSnapshot s=douyin.startLogin(accountId);if("CONNECTED".equals(s.status))store(accountId,platform,s.storageState);return com.aiprovider.logging.BusinessOperationLogger.success("service.PlatformAccountLoginService.start", vo(s));}throw new IllegalStateException("ADAPTER_UNAVAILABLE");}
    public PlatformLoginSessionVO poll(long accountId,String sessionId){
        com.aiprovider.logging.BusinessOperationLogger.start("service.PlatformAccountLoginService.poll", new String[] { "accountId", "sessionId" }, new Object[] { accountId, sessionId });
        String platform=platform(accountId);if("XIAOHONGSHU".equals(platform)){XiaohongshuWebAdapter.LoginSnapshot s=xhs.poll(accountId,sessionId);if("CONNECTED".equals(s.status))store(accountId,platform,s.storageState);return com.aiprovider.logging.BusinessOperationLogger.success("service.PlatformAccountLoginService.poll", vo(s));}if("DOUYIN".equals(platform)){DouyinWebAdapter.LoginSnapshot s=douyin.poll(accountId,sessionId);if("CONNECTED".equals(s.status))store(accountId,platform,s.storageState);return com.aiprovider.logging.BusinessOperationLogger.success("service.PlatformAccountLoginService.poll", vo(s));}throw new IllegalStateException("ADAPTER_UNAVAILABLE");}
    private String platform(long id){Map<String,Object> row=repository.findAccount(id);if(row==null)throw new IllegalArgumentException("ACCOUNT_NOT_FOUND");Object enabled=row.get("enabled");if(enabled instanceof Boolean&&!((Boolean)enabled))throw new IllegalStateException("ACCOUNT_DISABLED");return String.valueOf(row.get("platform"));}
    private void store(long id,String platform,String state){if(state==null||state.trim().isEmpty())throw new IllegalStateException("CREDENTIAL_MISSING");accounts.storeConnectedSecret(id,platform,"STORAGE_STATE",state,"扫码会话");}
    private PlatformLoginSessionVO vo(XiaohongshuWebAdapter.LoginSnapshot s){return new PlatformLoginSessionVO(s.sessionId,s.status,s.image,s.message);}private PlatformLoginSessionVO vo(DouyinWebAdapter.LoginSnapshot s){return new PlatformLoginSessionVO(s.sessionId,s.status,s.image,s.message);}
}
