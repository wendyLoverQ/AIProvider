package com.aiprovider.service;

import com.aiprovider.model.vo.AwsBillingMonitorVO;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.billing.BillingClient;
import software.amazon.awssdk.services.billing.model.Amount;
import software.amazon.awssdk.services.billing.model.CreditData;
import software.amazon.awssdk.services.billing.model.GetCreditsResponse;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.freetier.FreeTierClient;
import software.amazon.awssdk.services.freetier.model.FreeTierUsage;
import software.amazon.awssdk.services.freetier.model.GetAccountPlanStateResponse;
import software.amazon.awssdk.services.freetier.model.GetFreeTierUsageResponse;
import software.amazon.awssdk.services.freetier.model.MonetaryAmount;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsBillingMonitorServiceTest {
    @Test
    void aggregatesRealAwsBillingSourcesAndCachesTheSnapshot() {
        FreeTierClient freeTier=mock(FreeTierClient.class);CostExplorerClient cost=mock(CostExplorerClient.class);BillingClient billing=mock(BillingClient.class);
        when(freeTier.getAccountPlanState(any(software.amazon.awssdk.services.freetier.model.GetAccountPlanStateRequest.class))).thenReturn(
            GetAccountPlanStateResponse.builder().accountId("123456789012").accountPlanType("FREE").accountPlanStatus("ACTIVE")
                .accountPlanRemainingCredits(MonetaryAmount.builder().amount(87.5).unit("USD").build()).accountPlanExpirationDate(Instant.parse("2027-01-01T00:00:00Z")).build());
        when(freeTier.getFreeTierUsage(any(software.amazon.awssdk.services.freetier.model.GetFreeTierUsageRequest.class))).thenReturn(
            GetFreeTierUsageResponse.builder().freeTierUsages(Arrays.asList(
                FreeTierUsage.builder().service("AmazonEC2").actualUsageAmount(20.0).forecastedUsageAmount(40.0).limit(750.0).unit("Hrs").description("EC2 hours").build(),
                FreeTierUsage.builder().service("AmazonEBS").actualUsageAmount(25.0).forecastedUsageAmount(30.0).limit(30.0).unit("GB-Mo").description("EBS storage").build())).build());
        Map<String,MetricValue> totals=new HashMap<String,MetricValue>();
        totals.put("NetUnblendedCost",MetricValue.builder().amount("1.25").unit("USD").build());
        totals.put("UnblendedCost",MetricValue.builder().amount("2.50").unit("USD").build());
        when(cost.getCostAndUsage(any(software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest.class))).thenReturn(
            GetCostAndUsageResponse.builder().resultsByTime(ResultByTime.builder().total(totals).estimated(true).build()).build());
        when(billing.getCredits(any(software.amazon.awssdk.services.billing.model.GetCreditsRequest.class))).thenReturn(GetCreditsResponse.builder().credits(
            CreditData.builder().description("新用户额度").creditStatus("ACTIVE").initialAmount(Amount.builder().currencyAmount("100").currencyCode("USD").build())
                .remainingAmount(Amount.builder().currencyAmount("87.5").currencyCode("USD").build()).endDate(Instant.parse("2027-01-01T00:00:00Z")).build()).build());

        AwsBillingMonitorService service=new AwsBillingMonitorService(freeTier,cost,billing,ZoneId.of("Asia/Shanghai"),Duration.ofHours(6),"");
        AwsBillingMonitorVO result=service.current();
        assertTrue(result.getPlan().isAvailable());assertEquals("FREE",result.getPlan().getType());assertEquals(87.5,result.getPlan().getRemainingCredits());
        assertEquals(1.25,result.getCost().getNetUnblendedCost());assertTrue(result.getCost().isEstimated());
        assertEquals(87.5,result.getCredits().getRemainingAmount());assertEquals(1,result.getCredits().getItems().size());
        assertEquals("AmazonEBS",result.getFreeTier().getItems().get(0).getService());
        assertEquals(result,service.current());verify(cost,times(1)).getCostAndUsage(any(software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest.class));
    }

    @Test
    void reportsEachUnavailableSourceWithoutInventingZeroValues() {
        FreeTierClient freeTier=mock(FreeTierClient.class);CostExplorerClient cost=mock(CostExplorerClient.class);BillingClient billing=mock(BillingClient.class);
        when(freeTier.getAccountPlanState(any(software.amazon.awssdk.services.freetier.model.GetAccountPlanStateRequest.class))).thenThrow(new RuntimeException("denied"));
        when(freeTier.getFreeTierUsage(any(software.amazon.awssdk.services.freetier.model.GetFreeTierUsageRequest.class))).thenThrow(new RuntimeException("denied"));
        when(cost.getCostAndUsage(any(software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest.class))).thenThrow(new RuntimeException("denied"));
        AwsBillingMonitorVO result=new AwsBillingMonitorService(freeTier,cost,billing,ZoneId.of("UTC"),Duration.ofHours(6),"").current();
        assertFalse(result.getPlan().isAvailable());assertNull(result.getPlan().getRemainingCredits());
        assertFalse(result.getCost().isAvailable());assertNull(result.getCost().getNetUnblendedCost());
        assertFalse(result.getFreeTier().isAvailable());assertFalse(result.getCredits().isAvailable());
        assertEquals("AWS_ACCOUNT_ID_UNAVAILABLE",result.getCredits().getUnavailableReason());verifyNoInteractions(billing);
    }
}
