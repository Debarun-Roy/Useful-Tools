# Framework-neutral auth API 0.5.0 (implementation in progress)

All responses use application/json UTF-8, Cache-Control no-store, nosniff and a server-generated correlation header. Errors are {"error":"CONSTANT_CODE","requestId":"opaque-id"}; no submitted values. JSON duplicate keys, invalid UTF-8 and unknown fields fail 400. Body limit 16 KiB returns 413, unsupported media 415, unsupported method 405. No URL session identifiers. No credentialed wildcard CORS. Exact trusted Origin and X-CSRF-Token are required for unsafe methods. Cookies are opaque HttpOnly, production Secure, SameSite=Lax, context-path scoped, no Domain. Cookie name JSESSIONID.

| Method/path | Request | Success | Other outcomes |
|---|---|---|---|
| GET /api/auth/csrf-token | none | 200 {csrfToken:string}; creates anonymous state if absent | 429/503 |
| POST /api/auth/register | username,password and captchaToken only when CAPTCHA enabled | 201 {registered:true}; no authentication | 400/403/409 duplicate/429/503 |
| POST /api/auth/login | same as register | 200 {authenticated:true,user:{id,username,role}}; session and CSRF rotation | 401 INVALID_CREDENTIALS for unknown/wrong password; 409 already authenticated; 400/403/429/503 |
| GET /api/auth/session | none | 200 {authenticated:false} or {authenticated:true,user:{id,username,role}} | 429/503 |
| POST /api/auth/logout | empty JSON object | 204; cookie cleared, server session invalidated | 400/403/429/503 |
| GET /api/user/profile | none | 200 {id,username,role,displayName,preferences} | 401/404 if module absent/429/503 |
| PATCH /api/user/profile | nonempty subset of displayName:string, preferences:object | 200 same as GET; preferences wholly replaced | 400/401/403/404/429/503 |

Username is ASCII [A-Za-z0-9_]{3,32}, normalized lowercase, no trimming. Password is unchanged, default 15–128 code points, at most 512 UTF-8 bytes, no composition requirement. IDs and role user are server assigned. Profile rejects username/id/owner/role/hash fields; identity always comes from server session. Preference keys are [a-z][a-z0-9_]{0,31}, at most configured 20 entries, each string at most configured 200 characters; no nested values. Display name is bounded (default 100) and has no control characters.

CSRF bootstrap never grants identity. Stored-token absence fails closed on unsafe requests. Login changes the session ID and CSRF token; fetch the new token afterwards. Idle and absolute expiry invalidate state. Logout with absent/expired session remains Origin-protected but needs no missing-session token; existing anonymous/authenticated sessions require CSRF. Repeated logout returns 204. Old cookies cannot restore identity. No session persists across restart.

reCAPTCHA v3 actions are register/login. Enabled requests require captchaToken; missing or non-string request fields return 400 INVALID_FIELDS. Failed token/action/hostname/score/age/provider-field-type/replay validation returns 403 CAPTCHA_REJECTED; provider timeout/unavailability returns 503 CAPTCHA_UNAVAILABLE. Tokens are single-use at provider, maximum age 120 seconds, future skew at most 10 seconds. Disabled requests reject captchaToken as unknown. Secret comes only from a named environment variable. No real provider requests in tests.

Rate limiting returns 429 RATE_LIMITED with integer Retry-After seconds. Login/register have both per-address and normalized-account windows; other routes have address windows. Storage and hash concurrency are bounded. Limits are single-instance, not distributed. Busy hashing returns 503 AUTH_BUSY with Retry-After.

Client sequence: bootstrap token/cookie → register → login using same anonymous security state → refresh CSRF after rotation → GET session/profile → PATCH profile with current token → POST logout → GET session with old cookie proves anonymous. Login while authenticated is 409; register does not change identity.
