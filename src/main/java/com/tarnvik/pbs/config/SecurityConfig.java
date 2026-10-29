package com.tarnvik.pbs.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Google login for the owner, everything the friends need left open. Adapted from publicbackend's, without what
 * exists there only because of a separate frontend origin or a database: no CORS, no Spring Session, no pending
 * users. Sessions are in memory, so a restart logs the owner out.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  private final OwnerOnlyOidcUserService ownerOnlyOidcUserService;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        // The React app. Without this, a friend opening the public page would be sent to Google just to load the
        // JavaScript bundle. /favicon.ico is not served, but is permitted so that it gets its 404 rather than a
        // redirect: some browsers ask for it whatever index.html says.
        .requestMatchers("/", "/index.html", "/assets/**", "/icon.svg", "/favicon.ico").permitAll()
        // Spring Boot's error page. A 404 or 401 is rendered by forwarding here, and the forward is authorized
        // too: unpermitted, an anonymous 404 would turn into a redirect to Google.
        .requestMatchers("/error").permitAll()
        .requestMatchers("/ping").permitAll()
        .requestMatchers("/api/public/**").permitAll()
        // springdoc, which exists only in the `local` profile (a 404 elsewhere). Permitted so that code generation
        // can read the spec without a login.
        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
        // Everything else needs the owner, /api/auth/me and /api/protected/** included. Only the owner can log in
        // at all (OwnerOnlyOidcUserService), so authenticated means the owner.
        .anyRequest().authenticated()
      )
      .oauth2Login(oauth2 -> oauth2
        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(ownerOnlyOidcUserService))
        // Relative, so the same redirect works in production and through the Vite proxy in development.
        .defaultSuccessUrl("/#/upload", true)
        // Instead of Spring's generated /login page, which is English and offers the same Google button again.
        .failureUrl("/#/denied")
      )
      // POST /logout. A status rather than a redirect, because the app calls it with axios and then refetches
      // /api/auth/me itself.
      .logout(logout -> logout
        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
        .deleteCookies("JSESSIONID")
      )
      // 401 for /api/**, never a redirect to Google: a redirect on a fetch fails in a way that looks like a CORS
      // error and explains nothing. Every other path keeps oauth2Login's redirect.
      .exceptionHandling(ex -> ex
        .defaultAuthenticationEntryPointFor(
          (request, response, authException) -> {
            // TEMPORARY, for the phase 5 phone test: an upload turned away here never reaches the controller.
            log.info("401 for {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
          },
          PathPatternRequestMatcher.withDefaults().matcher("/api/**")
        )
      )
      // By default a refused request is saved in a new session, to return to it after login. A login here always
      // lands on /#/upload, so the saved request is never used, and every visitor's call to /api/auth/me would
      // leave a 30-minute session in memory for nothing.
      .requestCache(cache -> cache.requestCache(new NullRequestCache()))
      // Defensible only because the session cookie is first-party and SameSite=Lax (see application.properties):
      // a cross-site POST does not carry it.
      .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
