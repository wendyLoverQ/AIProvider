package com.aiprovider.config;

import com.aiprovider.service.HttpRequestMetricInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HttpRequestMetricConfig implements WebMvcConfigurer {
    private final HttpRequestMetricInterceptor interceptor;
    public HttpRequestMetricConfig(HttpRequestMetricInterceptor interceptor){this.interceptor=interceptor;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor).addPathPatterns("/api/**");}
}
