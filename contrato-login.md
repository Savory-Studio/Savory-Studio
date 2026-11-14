# REST Contract — User Login, JWT Issuance & Route Protection

**Jira Subtask:** [BE] APG-52 · [BE] APG-55 · [INT] APG-71

**Traceability:** AUTH-01 · AUTH-02 · AUTH-03 · PO-01 · RN-17 · RN-18

---

## POST /api/auth/login

### Description

Public endpoint that authenticates a user using their email and password. Upon successful verification against the stored BCrypt hash, issues a signed HS256 JSON Web Token (JWT) containing identifiable claims (`sub`, `email`, `firstName`, `iat`, `exp`) and a configurable expiration period.

---

### Request Body

```json
{
  "email": "carlos.perez@example.com",
  "password": "Password123!"
}
```

Both fields are required.

### Field Constraints

| Field | Type | Required | Constraints |
| :---- | :---- | :---- | :---- |
| `email` | String | Yes | Valid email format, non-blank |
| `password` | String | Yes | Non-blank |

---

### Success Response

**HTTP 200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsImVtYWlsIjoiY2FybG9zLnBlcmV6QGV4YW1wbGUuY29tIiwiaWF0IjoxNzYyNDYzMDAwLCJleHAiOjE3NjI0OTkwMDB9...",
  "tokenType": "Bearer",
  "expiresIn": "86400 seconds",
  "userId": 12,
  "email": "carlos.perez@example.com",
  "firstName": "Carlos",
  "lastName": "Pérez",
  "message": "Authentication successful."
}
```

---

### Error Responses

| Code | Scenario | Message | Rule |
| :---- | :---- | :---- | :---- |
| **400** | Missing `email` or `password` field | `"Email is required."` / `"Password is required."` | — |
| **400** | Invalid email syntax | `"Invalid email format."` | RN-14 |
| **400** | Malformed JSON body | `"El cuerpo de la petición es inválido o está vacío."` | — |
| **401** | User not found OR invalid password | `"Invalid credentials."` | RN-17 |

**Security Note (RN-17):** Error messages for authentication failures must remain generic (`"Invalid credentials."`) to prevent user enumeration attacks.

---

## Route Protection & Bearer Token Validation

All `/api/**` routes are protected by `SimulatedAuthFilter` + `JwtService`, with the following access matrix:

| Route Pattern | Access | Requirement |
| :---- | :---- | :---- |
| `POST /api/auth/register` | Public | No `Authorization` header required |
| `POST /api/auth/login` | Public | No `Authorization` header required |
| `/swagger-ui/**`, `/v3/api-docs/**` | Public | OpenAPI / Swagger UI documentation |
| `PATCH /api/users/me/password` | Protected | `Authorization: Bearer <JWT>` |
| `/api/promociones/**` | Protected | `Authorization: Bearer <JWT>` |
| `/api/coupons/**`, `/api/cupones/**` | Protected | `Authorization: Bearer <JWT>` |
| `/api/canjes/**` | Protected | `Authorization: Bearer <JWT>` |

### Protected Route Error Responses (401 Unauthorized)

| Code | Scenario | Message |
| :---- | :---- | :---- |
| **401** | Missing or non-Bearer `Authorization` header | `"The Bearer token is missing. Send Authorization: Bearer <token>."` |
| **401** | Tampered signature, expired JWT, or invalid token | `"The Bearer token is invalid or expired."` |

---

### Business Rules

| Rule | Description |
| :---- | :---- |
| **RN-17** | Generic Failure Message: Bad password or unregistered email must return HTTP 401 with a generic error message (`"Invalid credentials."`) without exposing whether the email exists. |
| **RN-18** | JWT Issuance & Validation: A valid login generates a signed JWT (HS256) containing `sub` (`userId`), `email`, and `firstName`, signed with the secret key (`JWT_SECRET`). Protected routes verify signature integrity and token expiration on every request. |

---

### QA Cross-Reference (Test Cases)

| Test Case | Scenario | Expected Result | Status |
| :---- | :---- | :---- | :---- |
| **CP-08** | Valid credentials (registered email and correct password) | HTTP 200 OK with JWT token, expiration time, and user info | Automated |
| **CP-09** | Non-existent email address | HTTP 401 Unauthorized with generic message `"Invalid credentials."` | Automated |
| **CP-10** | Registered email with incorrect password | HTTP 401 Unauthorized with generic message `"Invalid credentials."` | Automated |
| **CP-11** | Missing required fields (blank `email` or `password`) | HTTP 400 Bad Request | Automated |
| **CP-12** | Verification of JWT signature and expiration claim | Token successfully decoded and validated using system `JWT_SECRET` | Automated |
| **CP-13** | Authentication attempt with tampered JWT token signature | HTTP 401 Unauthorized upon access to protected route | Automated |
| **CP-14** | Authentication attempt with expired JWT token | HTTP 401 Unauthorized upon access to protected route | Automated |

---

### Data Model Dependencies

This contract operates on:
- `users` (`user_id`, `user_email`, `user_password_hash`, `user_first_name`, `user_last_name`)

---

## PATCH /api/users/me/password

**Jira Subtask:** [BE] APG-55

**Traceability:** AUTH-02 · AUTH-03

### Description

Protected endpoint that allows the currently authenticated user (identified exclusively from the validated Bearer token context) to change their password after verifying their current password against the stored BCrypt hash.

### Request Headers

- `Authorization: Bearer <JWT>` (required)
- `Content-Type: application/json`

*(In test environments using `TEST_AUTH_TOKEN`, `X-User-Id: <userId>` identifies the authenticated caller.)*

### Request Body

```json
{
  "currentPassword": "Password123!",
  "newPassword": "NewPassword456!"
}
```

Both fields are required. `oldPassword` is also accepted as a JSON alias for `currentPassword`.

### Field Constraints

| Field | Type | Required | Constraints |
| :---- | :---- | :---- | :---- |
| `currentPassword` | String | Yes | Non-blank; must match stored `user_password_hash` via `PasswordEncoder.matches` |
| `newPassword` | String | Yes | Non-blank, minimum 8 characters, maximum 72 UTF-8 bytes (BCrypt limit), must differ from the current password |

### Success Response

**HTTP 200 OK**

```json
{
  "userId": 12,
  "email": "carlos.perez@example.com",
  "message": "Password updated successfully."
}
```

Side effects on success:
- `users.user_password_hash` is updated exclusively with the new BCrypt hash (`$2a$...`).
- Plain-text passwords and BCrypt hashes are never returned in HTTP responses or logged.
- Subsequent calls to `POST /api/auth/login` succeed with `newPassword` and return `401 Unauthorized` with `currentPassword`.

### Error Responses

| Code | Scenario | Message |
| :---- | :---- | :---- |
| **400** | Missing/blank `currentPassword` or `newPassword` | `"Current password is required."` / `"New password is required."` |
| **400** | `newPassword` shorter than 8 characters | `"La contraseña debe tener al menos 8 caracteres."` |
| **400** | `newPassword` exceeds 72 UTF-8 bytes | `"Password exceeds the 72-byte BCrypt limit."` |
| **400** | `currentPassword` does not match stored BCrypt hash | `"Current password is incorrect."` |
| **400** | `newPassword` matches the existing password | `"New password must be different from the current password."` |
| **400** | Malformed or unreadable JSON body | `"El cuerpo de la petición es inválido o está vacío."` |
| **401** | Missing, invalid, or expired Bearer token | `"The Bearer token is missing. Send Authorization: Bearer <token>."` / `"The Bearer token is invalid or expired."` / `"Could not identify the authenticated user."` |
| **404** | Authenticated user ID does not exist in `users` | `"User not found."` |

### Security & No-Modification Guarantees

- Rejected requests (wrong current password, invalid new password, identical password, or unauthorized calls) leave `users.user_password_hash` unchanged.
- Neither plain-text passwords nor BCrypt hashes are persisted unencrypted or serialized in any response DTO.
- User identity is derived solely from the authenticated security context (`SecurityUtils`), never from client-supplied path or body IDs.

### QA Cross-Reference (Test Cases)

| Test Case | Scenario | Expected Result | Status |
| :---- | :---- | :---- | :---- |
| **CP-15** | Authenticated user provides valid `currentPassword` and valid `newPassword` | HTTP 200 OK without passwords/hashes in response; `user_password_hash` updated to new BCrypt hash | Automated |
| **CP-16** | Incorrect `currentPassword` | HTTP 400 Bad Request (`"Current password is incorrect."`); stored hash unchanged | Automated |
| **CP-17** | Invalid `newPassword` (`< 8` chars, `> 72` UTF-8 bytes, blank, or identical to current) | HTTP 400 Bad Request; stored hash unchanged | Automated |
| **CP-18** | Missing, tampered, or expired Bearer token | HTTP 401 Unauthorized | Automated |
| **CP-19** | Valid token whose `userId` does not exist in `users` | HTTP 404 Not Found (`"User not found."`) | Automated |
| **CP-20** | End-to-end login check after password change | `POST /api/auth/login` fails with old password (`401`) and succeeds with new password (`200`) | Automated |
