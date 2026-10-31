package com.boxoffice.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final DeviceLogin deviceLogin;
    private final RateLimits rateLimits;

    WebConfig(DeviceLogin deviceLogin, RateLimits rateLimits) {
        this.deviceLogin = deviceLogin;
        this.rateLimits = rateLimits;
    }

    /** Every /sync address needs a device token. /admin and /health don't. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(deviceLogin).addPathPatterns("/sync/**");
        // Day 123: placing orders and trying admin keys are rate limited, per IP address.
        registry.addInterceptor(rateLimits).addPathPatterns("/events/*/orders", "/admin/**");
    }
}
