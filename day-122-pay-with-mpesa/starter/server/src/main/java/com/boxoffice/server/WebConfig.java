package com.boxoffice.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final DeviceLogin deviceLogin;

    WebConfig(DeviceLogin deviceLogin) {
        this.deviceLogin = deviceLogin;
    }

    /** Every /sync address needs a device token. /admin and /health don't. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(deviceLogin).addPathPatterns("/sync/**");
    }
}
