package com.numaansystems.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter factory that relays the OAuth2 access token obtained during oauth2Login
 * to downstream services as an Authorization: Bearer <token> header.
 *
 * Usage in application.yml (filters): - TokenRelay=registrationId=azure,userHeader=X-User
 */
@Component
public class TokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<TokenRelayGatewayFilterFactory.Config> {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public TokenRelayGatewayFilterFactory(OAuth2AuthorizedClientService authorizedClientService) {
        super(Config.class);
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) ->
            ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .flatMap(auth -> addAuthHeaderIfPresent(exchange, auth, config))
                .flatMap(chain::filter);
    }

    private Mono<ServerWebExchange> addAuthHeaderIfPresent(ServerWebExchange exchange, Authentication auth, Config config) {
        if (auth == null || !auth.isAuthenticated()) {
            // Not authenticated: initiate login by redirecting to the oauth2 authorization endpoint
            String redirect = "/oauth2/authorization/" + config.getRegistrationId();
            exchange.getResponse().setStatusCode(HttpStatus.SEE_OTHER);
            exchange.getResponse().getHeaders().set(HttpHeaders.LOCATION, redirect);
            return Mono.just(exchange);
        }

        String principalName = auth.getName();

        // The synchronous OAuth2AuthorizedClientService provides loadAuthorizedClient which we call from a supplier
        return Mono.defer(() -> {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(config.getRegistrationId(), principalName);
                return Mono.justOrEmpty(client);
            })
            .flatMap(authorizedClient -> {
                if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                    String redirect = "/oauth2/authorization/" + config.getRegistrationId();
                    exchange.getResponse().setStatusCode(HttpStatus.SEE_OTHER);
                    exchange.getResponse().getHeaders().set(HttpHeaders.LOCATION, redirect);
                    return Mono.just(exchange);
                }

                String token = authorizedClient.getAccessToken().getTokenValue();
                exchange.getRequest().mutate()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();

                if (config.getUserHeader() != null && !config.getUserHeader().isBlank()) {
                    exchange.getRequest().mutate()
                        .header(config.getUserHeader(), principalName)
                        .build();
                }
                return Mono.just(exchange);
            })
            .switchIfEmpty(Mono.defer(() -> {
                String redirect = "/oauth2/authorization/" + config.getRegistrationId();
                exchange.getResponse().setStatusCode(HttpStatus.SEE_OTHER);
                exchange.getResponse().getHeaders().set(HttpHeaders.LOCATION, redirect);
                return Mono.just(exchange);
            }));
    }

    @Override
    public Config newConfig() {
        return new Config();
    }

    public static class Config {
        private String registrationId = "azure";
        private String userHeader;

        public String getRegistrationId() {
            return registrationId;
        }

        public void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }

        public String getUserHeader() {
            return userHeader;
        }

        public void setUserHeader(String userHeader) {
            this.userHeader = userHeader;
        }
    }
}
