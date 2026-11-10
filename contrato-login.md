# REST Contract — User Login, JWT Issuance & Route Protection

**Jira Subtask:** [BE] APG-52 · [INT] APG-71  
**Traceability:** AUTH-01 · PO-01 · RN-17 · RN-18

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
