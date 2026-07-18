package com.aiprovider.model.vo;

import java.util.List;
import java.util.Map;

public class ContentOperationsOverviewVO {
    private final ContentOperationSettingsVO settings; private final Map<String,Long> counters;
    private final List<ContentAccountVO> accounts; private final List<ContentSourceVO> sources; private final List<ContentPublicationVO> recentPublications;
    public ContentOperationsOverviewVO(ContentOperationSettingsVO settings,Map<String,Long> counters,List<ContentAccountVO> accounts,List<ContentSourceVO> sources,List<ContentPublicationVO> recentPublications){
        this.settings=settings;this.counters=counters;this.accounts=accounts;this.sources=sources;this.recentPublications=recentPublications;
    }
    public ContentOperationSettingsVO getSettings(){return settings;} public Map<String,Long> getCounters(){return counters;}
    public List<ContentAccountVO> getAccounts(){return accounts;} public List<ContentSourceVO> getSources(){return sources;}
    public List<ContentPublicationVO> getRecentPublications(){return recentPublications;}
}
