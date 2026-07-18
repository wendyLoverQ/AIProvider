package com.aiprovider.service;

import com.aiprovider.model.vo.CloudServerMonitorVO;
import com.aiprovider.model.vo.MonitorSummaryVO;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsCloudWatchTrafficServiceTest {
    @Test
    void returnsCloudWatchMonthlyTrafficAndPublishedFreeDtoQuota() {
        CloudWatchClient client=mock(CloudWatchClient.class);
        when(client.getMetricStatistics(any(GetMetricStatisticsRequest.class))).thenAnswer(invocation->{
            GetMetricStatisticsRequest request=invocation.getArgument(0);boolean live=request.period()==300;
            double sum="NetworkIn".equals(request.metricName())?(live?3600d:1000d):(live?1800d:400d);
            return GetMetricStatisticsResponse.builder().datapoints(Datapoint.builder().sum(sum).build()).build();
        });
        AwsCloudWatchTrafficService service=new AwsCloudWatchTrafficService(client,"i-test",ZoneId.of("Asia/Shanghai"),100_000_000_000L);

        MonitorSummaryVO.Traffic traffic=service.traffic();CloudServerMonitorVO.Network network=service.network();

        assertTrue(traffic.isAvailable());assertEquals(400L,traffic.getUsedBytes());assertEquals(100_000_000_000L,traffic.getTotalBytes());
        assertEquals("CLOUDWATCH_API_AWS_100GB_FREE_DTO",traffic.getStatus());assertTrue(network.isAvailable());
        assertEquals("CLOUDWATCH_API",network.getSource());assertEquals(1000L,network.getMonthInboundBytes());
        assertEquals(400L,network.getMonthOutboundBytes());assertEquals(2L,network.getInboundBytesPerSecond());
        assertEquals(1L,network.getOutboundBytesPerSecond());verify(client,times(4)).getMetricStatistics(any(GetMetricStatisticsRequest.class));
    }

    @Test
    void reportsUnavailableWhenCloudWatchRejectsTheRequestWithoutLocalFallback() {
        CloudWatchClient client=mock(CloudWatchClient.class);when(client.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
            .thenThrow(new IllegalStateException("denied"));
        AwsCloudWatchTrafficService service=new AwsCloudWatchTrafficService(client,"i-test",ZoneId.of("Asia/Shanghai"),100_000_000_000L);

        assertFalse(service.traffic().isAvailable());assertEquals("CLOUDWATCH_API_FAILED",service.traffic().getStatus());
        assertFalse(service.network().isAvailable());assertEquals("CLOUDWATCH_API_FAILED",service.network().getUnavailableReason());
    }
}
