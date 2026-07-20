package com.aiprovider.service;

import com.aiprovider.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class HttpRequestMetricInterceptorTest {
    @Test
    void recordsBusinessApiUsingMatchedRouteWithoutQueryData() throws Exception {
        MonitorRepository repository=mock(MonitorRepository.class);
        HttpServletRequest request=mock(HttpServletRequest.class);
        HttpServletResponse response=mock(HttpServletResponse.class);
        preserveAttributes(request);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/assets/42");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn("/api/assets/{id}");
        when(response.getStatus()).thenReturn(201);
        when(repository.recordHttpRequest(anyString(),anyString(),anyInt(),anyLong())).thenReturn(1);
        HttpRequestMetricInterceptor interceptor=new HttpRequestMetricInterceptor(repository);

        interceptor.preHandle(request,response,new Object());
        interceptor.afterCompletion(request,response,new Object(),null);

        verify(repository).recordHttpRequest(eq("POST"),eq("/api/assets/{id}"),eq(201),longThat(value -> value>=0));
    }

    @Test
    void excludesMonitorHealthStaticAndOptionsRequests() throws Exception {
        MonitorRepository repository=mock(MonitorRepository.class);
        assertExcluded(repository,"GET","/api/monitor/ai-overview");
        assertExcluded(repository,"GET","/api/monitor");
        assertExcluded(repository,"GET","/api/health");
        assertExcluded(repository,"GET","/api/health/");
        assertExcluded(repository,"GET","/assets/index.js");
        assertExcluded(repository,"OPTIONS","/api/assets");
        verifyNoInteractions(repository);
    }

    @Test
    void skipsApiRequestsWithoutANormalizedMatchedRoute() throws Exception {
        MonitorRepository repository=mock(MonitorRepository.class);
        HttpServletRequest request=mock(HttpServletRequest.class);HttpServletResponse response=mock(HttpServletResponse.class);
        preserveAttributes(request);when(request.getMethod()).thenReturn("GET");when(request.getRequestURI()).thenReturn("/api/private/value-in-path");
        HttpRequestMetricInterceptor interceptor=new HttpRequestMetricInterceptor(repository);
        interceptor.preHandle(request,response,new Object());interceptor.afterCompletion(request,response,new Object(),null);
        verifyNoInteractions(repository);
    }

    @Test
    void recordsUnhandledExceptionsAsServerFailures() throws Exception {
        MonitorRepository repository=mock(MonitorRepository.class);
        HttpServletRequest request=mock(HttpServletRequest.class);HttpServletResponse response=mock(HttpServletResponse.class);
        preserveAttributes(request);when(request.getMethod()).thenReturn("GET");when(request.getRequestURI()).thenReturn("/api/assets");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn("/api/assets");when(response.getStatus()).thenReturn(200);
        when(repository.recordHttpRequest(anyString(),anyString(),anyInt(),anyLong())).thenReturn(1);
        HttpRequestMetricInterceptor interceptor=new HttpRequestMetricInterceptor(repository);
        interceptor.preHandle(request,response,new Object());interceptor.afterCompletion(request,response,new Object(),new RuntimeException("failed"));
        verify(repository).recordHttpRequest(eq("GET"),eq("/api/assets"),eq(500),anyLong());
    }

    private void assertExcluded(MonitorRepository repository,String method,String uri) throws Exception {
        HttpServletRequest request=mock(HttpServletRequest.class);
        HttpServletResponse response=mock(HttpServletResponse.class);
        preserveAttributes(request);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        HttpRequestMetricInterceptor interceptor=new HttpRequestMetricInterceptor(repository);
        interceptor.preHandle(request,response,new Object());
        interceptor.afterCompletion(request,response,new Object(),null);
    }

    private void preserveAttributes(HttpServletRequest request){
        Map<String,Object> attributes=new HashMap<>();
        doAnswer(call -> {attributes.put(call.getArgument(0),call.getArgument(1));return null;}).when(request).setAttribute(anyString(),any());
        when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
    }
}
