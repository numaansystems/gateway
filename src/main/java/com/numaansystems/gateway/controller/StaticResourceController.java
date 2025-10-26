package com.numaansystems.gateway.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
public class StaticResourceController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> index() {
        try {
            Resource resource = new ClassPathResource("static/index.html");
            if (resource.exists()) {
                String content = Files.readString(Paths.get(resource.getURI()));
                return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content));
            }
        } catch (Exception e) {
            // Fall back to simple HTML
        }
        
        String simpleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Spring Cloud Gateway</title>
                <link rel="icon" type="image/x-icon" href="/favicon.ico">
            </head>
            <body>
                <h1>🚀 Spring Cloud Gateway</h1>
                <p>✅ Gateway is running successfully on port 9090</p>
                <h2>Quick Links</h2>
                <ul>
                    <li><a href="/gateway/actuator/health">Health Check</a></li>
                    <li><a href="/actuator/health">Direct Health</a></li>
                    <li><a href="/gateway/actuator">All Actuator Endpoints</a></li>
                    <li><a href="/gateway/httpbin/get">Test Route</a></li>
                </ul>
            </body>
            </html>
            """;
        
        return Mono.just(ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(simpleHtml));
    }

    @GetMapping(value = "/favicon.ico", produces = "image/x-icon")
    public Mono<ResponseEntity<byte[]>> favicon() {
        try {
            Resource resource = new ClassPathResource("static/favicon.ico");
            if (resource.exists()) {
                byte[] content = Files.readAllBytes(Paths.get(resource.getURI()));
                return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/x-icon"))
                    .body(content));
            }
        } catch (Exception e) {
            // Return 404 if favicon not found
        }
        
        return Mono.just(ResponseEntity.notFound().build());
    }
}