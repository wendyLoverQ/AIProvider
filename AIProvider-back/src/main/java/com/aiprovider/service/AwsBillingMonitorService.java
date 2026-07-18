package com.aiprovider.service;

import com.aiprovider.model.vo.AwsBillingMonitorVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.billing.BillingClient;
import software.amazon.awssdk.services.billing.model.Amount;
import software.amazon.awssdk.services.billing.model.CreditData;
import software.amazon.awssdk.services.billing.model.GetCreditsRequest;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.freetier.FreeTierClient;
import software.amazon.awssdk.services.freetier.model.FreeTierUsage;
import software.amazon.awssdk.services.freetier.model.GetAccountPlanStateResponse;
import software.amazon.awssdk.services.freetier.model.GetAccountPlanStateRequest;
import software.amazon.awssdk.services.freetier.model.GetFreeTierUsageRequest;
import software.amazon.awssdk.services.freetier.model.GetFreeTierUsageResponse;
import software.amazon.awssdk.services.freetier.model.MonetaryAmount;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AwsBillingMonitorService {
    private static final Logger log=LogManager.getLogger(AwsBillingMonitorService.class);
    private final FreeTierClient freeTierClient; private final CostExplorerClient costClient; private final BillingClient billingClient;
    private final ZoneId zone; private final Duration cacheTtl; private final String configuredAccountId;
    private final Object lock=new Object(); private volatile Cached cached;

    @Autowired
    public AwsBillingMonitorService(@Value("${monitor.timezone:Asia/Shanghai}") String timezone,
                                    @Value("${monitor.aws.billing-cache-hours:6}") long cacheHours,
                                    @Value("${monitor.aws.account-id:}") String accountId) {
        this(FreeTierClient.builder().region(Region.US_EAST_1).credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(awsTimeouts()).build(),
            CostExplorerClient.builder().region(Region.US_EAST_1).credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(awsTimeouts()).build(),
            BillingClient.builder().region(Region.US_EAST_1).credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(awsTimeouts()).build(),
            ZoneId.of(timezone),Duration.ofHours(Math.max(1,cacheHours)),accountId);
    }

    private static ClientOverrideConfiguration awsTimeouts() { return ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(10)).build(); }

    AwsBillingMonitorService(FreeTierClient freeTierClient,CostExplorerClient costClient,BillingClient billingClient,
                             ZoneId zone,Duration cacheTtl,String accountId) {
        this.freeTierClient=freeTierClient;this.costClient=costClient;this.billingClient=billingClient;this.zone=zone;
        this.cacheTtl=cacheTtl;this.configuredAccountId=accountId==null?"":accountId.trim();
    }

    public AwsBillingMonitorVO current() {
        Instant now=Instant.now();Cached value=cached;
        if(value!=null&&Duration.between(value.at,now).compareTo(cacheTtl)<0)return value.value;
        synchronized(lock){now=Instant.now();value=cached;if(value!=null&&Duration.between(value.at,now).compareTo(cacheTtl)<0)return value.value;
            AwsBillingMonitorVO result=fetch();cached=new Cached(now,result);return result;}
    }

    private AwsBillingMonitorVO fetch() {
        OffsetDateTime collectedAt=OffsetDateTime.now(zone);PlanResult plan=readPlan();
        return new AwsBillingMonitorVO(plan.value,readCost(),readCredits(plan.accountId),readFreeTier(),collectedAt);
    }

    private PlanResult readPlan() {
        try {
            GetAccountPlanStateResponse response=freeTierClient.getAccountPlanState(GetAccountPlanStateRequest.builder().build());MonetaryAmount amount=response.accountPlanRemainingCredits();
            Double remaining=amount==null?null:amount.amount();String currency=amount==null?null:amount.unitAsString();
            AwsBillingMonitorVO.Plan value=new AwsBillingMonitorVO.Plan(true,null,response.accountPlanTypeAsString(),response.accountPlanStatusAsString(),
                remaining,currency,toOffset(response.accountPlanExpirationDate()));
            return new PlanResult(value,response.accountId());
        } catch(Exception exception) {
            warn("FREE_TIER_PLAN_API_FAILED",exception);return new PlanResult(new AwsBillingMonitorVO.Plan(false,reason(exception),null,null,null,null,null),configuredAccountId);
        }
    }

    private AwsBillingMonitorVO.FreeTier readFreeTier() {
        try {
            List<AwsBillingMonitorVO.Usage> items=new ArrayList<AwsBillingMonitorVO.Usage>();String token=null;
            do {
                GetFreeTierUsageResponse response=freeTierClient.getFreeTierUsage(GetFreeTierUsageRequest.builder().maxResults(100).nextToken(token).build());
                for(FreeTierUsage item:response.freeTierUsages())items.add(new AwsBillingMonitorVO.Usage(item.service(),item.operation(),item.usageType(),item.region(),
                    item.actualUsageAmount(),item.forecastedUsageAmount(),item.limit(),item.unit(),item.description(),item.freeTierType()));
                token=response.nextToken();
            } while(token!=null&&!token.isEmpty());
            items.sort(Comparator.comparing(AwsBillingMonitorVO.Usage::getUsagePercent,Comparator.nullsLast(Comparator.reverseOrder())));
            return new AwsBillingMonitorVO.FreeTier(true,null,items);
        } catch(Exception exception) {warn("FREE_TIER_USAGE_API_FAILED",exception);return new AwsBillingMonitorVO.FreeTier(false,reason(exception),null);}
    }

    private AwsBillingMonitorVO.Cost readCost() {
        LocalDate today=LocalDate.now(zone),start=today.withDayOfMonth(1),end=today.plusDays(1);
        try {
            GetCostAndUsageResponse response=costClient.getCostAndUsage(GetCostAndUsageRequest.builder().timePeriod(DateInterval.builder().start(start.toString()).end(end.toString()).build())
                .granularity(Granularity.MONTHLY).metrics("NetUnblendedCost","UnblendedCost").build());
            ResultByTime result=response.resultsByTime().isEmpty()?null:response.resultsByTime().get(0);
            if(result==null)return new AwsBillingMonitorVO.Cost(false,"COST_EXPLORER_EMPTY",start.toString(),end.toString(),null,null,null,false);
            MetricValue net=result.total().get("NetUnblendedCost"),gross=result.total().get("UnblendedCost");
            return new AwsBillingMonitorVO.Cost(true,null,start.toString(),end.toString(),number(net),number(gross),
                net!=null?net.unit():gross==null?null:gross.unit(),Boolean.TRUE.equals(result.estimated()));
        } catch(Exception exception) {warn("COST_EXPLORER_API_FAILED",exception);return new AwsBillingMonitorVO.Cost(false,reason(exception),start.toString(),end.toString(),null,null,null,false);}
    }

    private AwsBillingMonitorVO.Credits readCredits(String planAccountId) {
        String accountId=configuredAccountId.isEmpty()?planAccountId:configuredAccountId;
        if(accountId==null||!accountId.matches("\\d{12}"))return new AwsBillingMonitorVO.Credits(false,"AWS_ACCOUNT_ID_UNAVAILABLE",null,null,null,null);
        try {
            List<CreditData> credits=billingClient.getCredits(GetCreditsRequest.builder().accountId(accountId).startDate(Instant.now().minus(Duration.ofDays(365))).build()).credits();
            List<AwsBillingMonitorVO.Credit> items=new ArrayList<AwsBillingMonitorVO.Credit>();double initial=0,remaining=0;String currency=null;
            for(CreditData credit:credits){Double initialValue=number(credit.initialAmount()),remainingValue=number(credit.remainingAmount());
                if(initialValue!=null)initial+=initialValue;if(remainingValue!=null)remaining+=remainingValue;
                if(currency==null&&credit.remainingAmount()!=null)currency=credit.remainingAmount().currencyCode();
                items.add(new AwsBillingMonitorVO.Credit(credit.description(),credit.creditStatusAsString(),initialValue,remainingValue,
                    credit.remainingAmount()==null?null:credit.remainingAmount().currencyCode(),toOffset(credit.endDate())));
            }
            items.sort(Comparator.comparing(AwsBillingMonitorVO.Credit::getExpirationDate,Comparator.nullsLast(Comparator.naturalOrder())));
            return new AwsBillingMonitorVO.Credits(true,null,remaining,initial,currency,items);
        } catch(Exception exception) {warn("BILLING_CREDITS_API_FAILED",exception);return new AwsBillingMonitorVO.Credits(false,reason(exception),null,null,null,null);}
    }

    private Double number(MetricValue value){return value==null?null:number(value.amount());}
    private Double number(Amount value){return value==null?null:number(value.currencyAmount());}
    private Double number(String value){try{return value==null?null:Double.valueOf(value);}catch(NumberFormatException ignored){return null;}}
    private OffsetDateTime toOffset(Instant value){return value==null?null:value.atZone(zone).toOffsetDateTime();}
    private String reason(Exception exception){String name=exception.getClass().getSimpleName();return name==null||name.isEmpty()?"AWS_API_FAILED":name;}
    private void warn(String code,Exception exception){log.warn("AWS billing monitor query failed code={} type={}",code,exception.getClass().getSimpleName());}
    private static class PlanResult {final AwsBillingMonitorVO.Plan value;final String accountId;PlanResult(AwsBillingMonitorVO.Plan value,String accountId){this.value=value;this.accountId=accountId;}}
    private static class Cached {final Instant at;final AwsBillingMonitorVO value;Cached(Instant at,AwsBillingMonitorVO value){this.at=at;this.value=value;}}
}
