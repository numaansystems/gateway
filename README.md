# Spring Cloud Gateway - Azure AD OAuth2 Gateway

This repository implements a Spring Cloud Gateway which centralizes authentication using Azure Active Directory (Azure AD) and relays access tokens to downstream applications. It aims to provide SSO and minimal integration friction for both modern and legacy applications.

## Features

- Azure AD OAuth2 login (authorization code / OpenID Connect)
- Single sign-on handled by the Gateway
- Token relay: gateway attaches `Authorization: Bearer <access_token>` header to proxied requests
- Optional `X-Forwarded-User` header with the authenticated username (useful for legacy apps)
- Route configuration via `application.yml`

## Project layout

- `src/main/java/.../GatewayApplication.java` - main class
- `src/main/java/.../config/SecurityConfig.java` - reactive security configuration
- `src/main/java/.../filter/TokenRelayGatewayFilterFactory.java` - custom gateway filter to relay tokens
- `src/main/resources/application.yml` - route and oauth2 configuration

## Quickstart

1. Register an application in Azure AD
   - In Azure Portal -> Azure Active Directory -> App registrations -> New registration.
   - Set Redirect URI to `https://<gateway-host>/login/oauth2/code/azure` (or `http://localhost:8080/login/oauth2/code/azure` for local testing).
   - Copy `Application (client) ID` into `AZURE_CLIENT_ID` and `Directory (tenant) ID` into `AZURE_TENANT_ID`.
   - Add a client secret and copy it to `AZURE_CLIENT_SECRET`.

2. Configure the Gateway
   - Provide environment variables or override `application.yml` properties:
     - `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`

3. Routes
   - Two example routes are defined in `application.yml`:
     - `/client1/**` -> `http://localhost:8081` (modern Spring Boot 3 application)
     - `/client2/**` -> `http://localhost:8082` (legacy Spring Framework 3 application)
   - Both routes use the `TokenRelay` filter to attach the OAuth2 access token and a header `X-Forwarded-User` with the principal name.

4. Run locally

```bash
cd /workspaces/gateway
mvn -DskipTests package
java -jar target/gateway-0.1.0.jar
```

5. Access
   - Browse to `http://localhost:8080/client1/` — you'll be redirected to Azure AD to sign in. After login you'll be proxied to the target backend with an `Authorization` header containing the bearer token.

## Onboarding clients

Modern Spring Boot (Client 1)
- Use the Authorization header for API calls. If the application is server-side rendered (Thymeleaf), it can receive the forwarded user header (`X-Forwarded-User`) for UI personalization.

Legacy Spring Framework 3 (Client 2)
- Minimal integration change: the gateway forwards the `Authorization: Bearer` token and `X-Forwarded-User`. If the legacy app cannot handle bearer tokens, it can rely on the `X-Forwarded-User` header to recognize the user. For stronger security, add a shared trust layer (e.g., mutual TLS between gateway and backend, or validate a signed token/header) — see Security Notes.

## Security notes

- The TokenRelay filter reads the access token for the currently authenticated user and adds it to downstream requests.
- For legacy apps that do not validate tokens, forwarding `X-Forwarded-User` requires trust: ensure backends only accept traffic from the gateway (network rules, mTLS, firewall).
- Consider adding `spring.security.oauth2.resourceserver.jwt` validation on backend services when they understand JWTs.

## Configuration examples

- To use a shared property file or environment variables, replace placeholders in `application.yml` with actual values or set `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, and `AZURE_TENANT_ID` environment variables.

## Next steps & improvements

- Add token exchange or on-demand token acquisition (OAuth2AuthorizedClientManager) for client credentials scenario
- Add a small admin UI or dynamic route configuration service
- Add health checks, Prometheus metrics, and distributed tracing

## Acceptance

This gateway provides Azure AD OAuth2 login, route configuration via `application.yml`, and token relay to multiple downstream applications (modern and legacy). See the `application.yml` for example route setups.
