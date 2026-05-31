# Security Standards

## Authentication & Authorization

- **Spring Security** with session-based authentication.
- All `/api/**` endpoints require authentication unless explicitly permitted.
- Use method-level security (`@PreAuthorize`) for fine-grained access control.
- Passwords stored using BCrypt (strength ≥ 10).

## Input Validation

- Validate all input at the controller layer using Bean Validation (`@Valid`).
- Sanitize file names for upload/download operations.
- Reject path traversal sequences (`../`) in any user-supplied path.
- Limit request body size (configure in application properties).
- Limit file upload size (receipts).

## Output Encoding

- Backend: Jackson handles JSON encoding automatically.
- Frontend: Always use the `esc()` helper before injecting user data into HTML/DOM.
- Set `Content-Type` headers explicitly on all responses.

## Secrets Management

- **Never** commit secrets, API keys, or credentials to source control.
- Use environment variables or external config files (excluded from assembly/jar).
- `.yml` config files are excluded from the jar via Maven config.

## HTTP Security Headers

Configure via Spring Security or a filter:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0 (rely on CSP instead)
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:
Strict-Transport-Security: max-age=31536000; includeSubDomains (if HTTPS)
```

## CSRF

- CSRF protection enabled for browser-based sessions.
- API calls from the SPA must include the CSRF token (cookie-to-header pattern).

## Rate Limiting

- Apply rate limiting on authentication endpoints to prevent brute force.
- Consider rate limiting AI/OCR endpoints due to cost.

## Dependency Security

- Regularly audit dependencies: `mvn dependency:tree`, `mvn versions:display-dependency-updates`.
- Monitor CVEs via GitHub Dependabot or OWASP Dependency-Check plugin.
- Never use dependencies with known critical CVEs in production.

## Logging & Auditing

- Log authentication events (login, logout, failed attempts).
- Never log passwords, tokens, or full credit card numbers.
- Log access to sensitive operations (delete, share, export).

## File Upload Security

- Validate MIME type and file extension for receipt uploads.
- Store uploads outside the web root or in a non-executable directory.
- Generate random file names to prevent enumeration.
- Scan for malicious content if feasible.

