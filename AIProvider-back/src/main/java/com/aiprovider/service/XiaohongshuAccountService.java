package com.aiprovider.service;

import com.aiprovider.model.vo.XhsLoginSessionVO;
import com.aiprovider.repository.ContentOperationsRepository;
import org.springframework.stereotype.Service;

@Service
public class XiaohongshuAccountService {
    private final ContentOperationsRepository repository;private final PlatformAccountService accounts;private final XiaohongshuWebAdapter adapter;
    public XiaohongshuAccountService(ContentOperationsRepository repository,PlatformAccountService accounts,XiaohongshuWebAdapter adapter){this.repository=repository;this.accounts=accounts;this.adapter=adapter;}
    public XhsLoginSessionVO startLogin(long accountId){requiredAccount(accountId);XiaohongshuWebAdapter.LoginSnapshot snapshot=adapter.startLogin(accountId);return vo(snapshot);}
    public XhsLoginSessionVO poll(long accountId,String sessionId){java.util.Map<String,Object> account=requiredAccount(accountId);XiaohongshuWebAdapter.LoginSnapshot snapshot=adapter.poll(accountId,sessionId);if("CONNECTED".equals(snapshot.status)){if(snapshot.storageState==null)throw new IllegalStateException("小红书登录会话缺少状态数据");Object platformAccountId=account.get("platformAccountId");if(!(platformAccountId instanceof Number))throw new IllegalStateException("小红书业务账号尚未绑定账号中心");accounts.storeConnectedSecret(((Number)platformAccountId).longValue(),"XIAOHONGSHU","STORAGE_STATE",snapshot.storageState,"扫码会话");}return vo(snapshot);}
    private java.util.Map<String,Object> requiredAccount(long id){java.util.Map<String,Object> account=repository.findAccount(id);if(account==null)throw new IllegalArgumentException("小红书账号不存在");return account;}private XhsLoginSessionVO vo(XiaohongshuWebAdapter.LoginSnapshot s){return new XhsLoginSessionVO(s.sessionId,s.status,s.image,s.message);}
}
