package com.tarnvik.pbs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Cache headers for the React app. Vite puts a content hash in every file name under /assets, so such a file can
 * never be stale and may be kept for a year. index.html names those files, so it must be revalidated on every load:
 * without a Cache-Control header a browser may guess a lifetime for it and keep showing the previous release after
 * a deploy. The welcome page at / forwards to /index.html and gets the same header.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/assets/**")
      .addResourceLocations("classpath:/static/assets/")
      .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    registry.addResourceHandler("/index.html")
      .addResourceLocations("classpath:/static/")
      .setCacheControl(CacheControl.noCache());
  }
}
