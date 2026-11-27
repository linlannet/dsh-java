# AGENTS.md — dsh-java

## Project
Java 17 + Spring Boot 3.5.16 **Agent Harness** (ReAct loop, tools, approvals, plugins, connector accounts/OAuth, event sourcing). Version 0.2.3.

## Layout
- `dsh-java-domain` models, events, ports
- `dsh-java-infrastructure` adapters (DB, LLM, plugin runtime)
- `dsh-java-case` use cases (agent loop, session, task, approval)
- `dsh-java-api` interfaces, DTOs, `Response` envelope
- `dsh-java-trigger` HTTP/SSE controllers, filters
- `dsh-java-app` Spring Boot app + static web console
- `dsh-java-trigger/http/EmisOidcController` — enterprise identity OIDC login/callback/me/logout (PKCE; RS256 token kept in HttpSession)
- `dsh-java-api/dto/EmisIdentityDTO` — identity DTO returned to the web console
- `plugins/sample-tools-plugin`
- `plugins/dsh-emis-plugin` (linlan-emis identity: RS256 JWKS, whoami/scope_check)

Dependency direction: `trigger -> api <- case -> domain`, `infrastructure` implements domain ports.

## Build & Test
- Build: `mvn clean package -DskipTests`
- Test: `mvn test`
- Single module: `mvn -pl dsh-java-case -am test -Dsurefire.failIfNoSpecifiedTests=false`
- Offline: append `-o` when the local Maven cache is warm

## Conventions
- Java 17 records for DTOs / value objects
- Return `net.linlan.dsh.api.response.Response<T>` from use cases
- Controllers only translate protocol; business rules live in `cases`; `*CaseImpl` delegates through a `*Factory`
- Connector tokens stay behind domain ports/server cache and never enter DTOs, browser state or logs
- No new third-party dependency without explicit justification
- Docs are Chinese by default; keep `README.md` and `README.en.md` in sync

## Do Not
- Do not edit or commit `target/`
- Do not put business rules in `trigger`
- Do not log secrets (API keys, tokens, passwords)
- Do not add new Maven modules without updating the root `pom.xml`

## Key Docs
- `README.md`, `README.en.md`
- `docs/md/java-plugin-development.md`
- `docs/spec/0.2.3/`
- `llms.txt`