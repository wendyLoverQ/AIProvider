package com.aiprovider.model.vo;

public class PlatformAccountUsageVO {
    private final String consumerType;private final long consumerId;private final String consumerName;
    public PlatformAccountUsageVO(String type,long id,String name){consumerType=type;consumerId=id;consumerName=name;}public String getConsumerType(){return consumerType;}public long getConsumerId(){return consumerId;}public String getConsumerName(){return consumerName;}
}
