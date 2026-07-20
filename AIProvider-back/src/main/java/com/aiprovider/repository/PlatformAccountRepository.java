package com.aiprovider.repository;

import com.aiprovider.mapper.PlatformAccountMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class PlatformAccountRepository {
    private final PlatformAccountMapper mapper;
    public PlatformAccountRepository(PlatformAccountMapper mapper){this.mapper=mapper;}

    public long insertAccount(PlatformAccountMapper.AccountRecord record){
        int affected=mapper.insertAccount(record);
        if(affected!=1||record.getId()==null)throw new IllegalStateException("账号新增影响行数不一致");
        return record.getId();
    }
    public void updateAccount(PlatformAccountMapper.AccountRecord record){if(mapper.updateAccount(record)!=1)throw new IllegalStateException("账号更新影响行数不一致");}
    public Map<String,Object> findAccount(long id){return mapper.findAccount(id);}
    public List<Map<String,Object>> findAccounts(String query,String platform,String accountKind,String status,int offset,int limit){return mapper.findAccounts(query,platform,accountKind,status,offset,limit);}
    public long countAccounts(String query,String platform,String accountKind,String status){return mapper.countAccounts(query,platform,accountKind,status);}
    public List<Map<String,Object>> findSecretSummaries(long accountId){return mapper.findSecretSummaries(accountId);}
    public Map<String,Object> findSecret(long accountId,String type){return mapper.findSecret(accountId,type);}
    public void upsertSecret(long accountId,String type,String encrypted,String hint){
        Map<String,Object> existing=mapper.findSecret(accountId,type);
        if(existing==null){PlatformAccountMapper.SecretRecord record=new PlatformAccountMapper.SecretRecord();record.setAccountId(accountId);record.setSecretType(type);record.setEncryptedValue(encrypted);record.setSecretHint(hint);if(mapper.insertSecret(record)!=1||record.getId()==null)throw new IllegalStateException("凭据新增影响行数不一致");return;}
        long id=number(existing.get("id"));int version=integer(existing.get("secretVersion"))+1;
        if(mapper.updateSecret(id,encrypted,hint,version)!=1)throw new IllegalStateException("凭据更新影响行数不一致");
    }
    public void updateStatus(long id,String status,String hint,boolean validated,String errorCode,String errorMessage){if(mapper.updateStatus(id,status,hint,validated,errorCode,errorMessage)!=1)throw new IllegalStateException("账号状态更新影响行数不一致");}
    public List<Map<String,Object>> findUsages(long id){return mapper.findUsages(id);}
    public void archiveAccount(long id){if(!findUsages(id).isEmpty())throw new IllegalStateException("ACCOUNT_IN_USE");if(mapper.archiveAccount(id)!=1)throw new IllegalStateException("账号归档影响行数不一致");}
    public Map<String,Object> findByLegacy(String type,long id){return mapper.findByLegacy(type,id);}
    private long number(Object v){return v instanceof Number?((Number)v).longValue():Long.parseLong(String.valueOf(v));}
    private int integer(Object v){return v instanceof Number?((Number)v).intValue():Integer.parseInt(String.valueOf(v));}
}
