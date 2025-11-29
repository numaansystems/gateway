# Legacy App Integration Guide

This document provides a comprehensive guide to integrating with the legacy Spring 3 application.

## 1. Authentication Filter Configuration

### AuthenticationFilter.java
```java
package com.example.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code, if necessary
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Authentication logic
        String authToken = req.getHeader("Authorization");
        if (authToken == null || !isValid(authToken)) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isValid(String token) {
        // Validate the token
        return true; // Stub for example
    }

    @Override
    public void destroy() {
        // Cleanup code if necessary
    }
}
```


## 2. Auth Callback Controller

### AuthCallbackController.java
```java
package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthCallbackController {

    @RequestMapping(value = "/auth/callback", method = RequestMethod.GET)
    public String handleAuthCallback(@RequestParam("code") String code) {
        // Logic to handle the callback
        return "redirect:/home";
    }
}
```


## 3. web.xml Configuration
```xml
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee 
         http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd" 
         version="3.1">

    <filter>
        <filter-name>authenticationFilter</filter-name>
        <filter-class>com.example.filter.AuthenticationFilter</filter-class>
    </filter>

    <filter-mapping>
        <filter-name>authenticationFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

    <servlet>
        <servlet-name>dispatcher</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>dispatcher</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>

</web-app>
```

## Conclusion
This guide has covered the necessary components for integrating with the legacy application including the authentication filter, the callback controller, and the necessary web.xml configurations.
