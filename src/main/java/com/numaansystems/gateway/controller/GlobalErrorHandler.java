package com.numaansystems.gateway.controller;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@Order(-2) // Higher priority than DefaultErrorWebExceptionHandler
public class GlobalErrorHandler extends AbstractErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);

    public GlobalErrorHandler(ErrorAttributes errorAttributes, 
                            ApplicationContext applicationContext, 
                            ServerCodecConfigurer configurer) {
        super(errorAttributes, new WebProperties.Resources(), applicationContext);
        this.setMessageWriters(configurer.getWriters());
        this.setMessageReaders(configurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> errorAttributes = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        Throwable error = getError(request);
        
        // Log the error for monitoring
        logger.error("Gateway error occurred: {}", error.getMessage(), error);
        
        HttpStatus status = determineHttpStatus(error);
        
        Map<String, Object> response = Map.of(
            "error", status.getReasonPhrase(),
            "message", getErrorMessage(error, status),
            "timestamp", LocalDateTime.now(),
            "path", request.path(),
            "status", status.value()
        );

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(response));
    }

    private HttpStatus determineHttpStatus(Throwable error) {
        if (error instanceof org.springframework.security.oauth2.core.OAuth2AuthorizationException) {
            return HttpStatus.UNAUTHORIZED;
        } else if (error instanceof java.net.ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String getErrorMessage(Throwable error, HttpStatus status) {
        switch (status) {
            case UNAUTHORIZED:
                return "Authentication required. Please log in to continue.";
            case SERVICE_UNAVAILABLE:
                return "The requested service is temporarily unavailable. Please try again later.";
            case GATEWAY_TIMEOUT:
                return "The request timed out. Please try again.";
            default:
                return "An unexpected error occurred. Please contact support if the issue persists.";
        }
    }
}