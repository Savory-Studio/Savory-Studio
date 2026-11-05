# REST Contract — User Registration

**Jira Subtask:** [BE] APG-51
**Traceability:** RF-03 · RN-14 · RN-15 · RN-16

---

## POST /api/auth/register

### Description

Allows a new user to register an account by providing their personal information, unique email, and password. The password is strictly encrypted using **BCrypt** before being stored. This endpoint is dedicated solely to user creation; it does not issue JWT tokens or handle user login.

### Request Body

```json
{
  "firstName": "Carlos",
  "lastName": "Pérez",
  "birthDate": "1995-05-15",
  "email": "carlos.perez@example.com",
  "password": "Password123!"
}
```

All fields are required.

### Field Constraints

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `firstName` | String | Yes | Non-blank, max 50 characters |
| `lastName` | String | Yes | Non-blank, max 50 characters |
| `birthDate` | LocalDate | Yes | Format `YYYY-MM-DD`, valid date |
| `email` | String | Yes | Valid email syntax, must be unique in the database |
| `password` | String | Yes | Minimum 8 characters, hashed with BCrypt prior to storage |

### Success Response

**HTTP 201 Created**

```json
{
  "userId": 12,
  "firstName": "Carlos",
  "lastName": "Pérez",
  "birthDate": "1995-05-15",
  "email": "carlos.perez@example.com",
  "createdAt": "2026-11-04T19:00:00",
  "message": "User registered successfully."
}
```

Side effects on success:
- A new row is inserted in the `users` table.
- The password is saved **exclusively as a BCrypt hash** and is never returned in the response.
- `created_at` is set to the current server timestamp.

---

### Error Responses

| Code | Scenario | Message | Rule |
|------|----------|---------|------|
| 400 | Required field missing or blank (`firstName`, `email`, etc.) | "The field {field} is required." | — |
| 400 | Invalid email syntax | "Invalid email format." | RN-14 |
| 400 | Password too short (less than 8 characters) | "Password must be at least 8 characters long." | RN-15 |
| 400 | Malformed JSON or unreadable body | "El cuerpo de la petición es inválido o está vacío." | — |
| 409 | Email is already registered | "Email address is already registered." | RN-16 |

---

### No-Modification Guarantee

When any error response is returned, the database state remains unchanged. No user record is created and no password hash is generated.

---

### Authorization

Public — Endpoint is open and does not require authentication headers (`SimulatedAuthFilter`).

---

### Business Rules

| Rule | Description |
|------|-------------|
| RN-14 | The `email` field must conform to standard email syntax. |
| RN-15 | The provided password must meet the minimum length requirements and must be stored hashed using BCrypt. Plain-text passwords must never be persisted or returned. |
| RN-16 | Email uniqueness: No two users can share the same `email` (`user_email`). |

---

### QA Cross-Reference

| Test Case | Scenario | Expected | Status |
|-----------|----------|----------|--------|
| CP-01     | Valid registration data and non-existent email | 201 Created without password in response | Pending |
| CP-02     | Missing required field (e.g., `email` or `password`) | 400 Bad Request | Pending |
| CP-03     | Invalid email syntax (e.g., "invalid-email") | 400 Bad Request | Pending |
| CP-04     | Password with fewer than 8 characters | 400 Bad Request | Pending |
| CP-05     | Registration attempt with an existing email | 409 Conflict | Pending |
| CP-06     | Database verification: confirm stored password is a `$2a$` hash | BCrypt hash confirmed in DB | Pending |
| CP-07     | Malformed or empty request body | 400 Bad Request | Pending |

---

### Data Model Dependencies

This contract operates on:
- `users` (`user_id`, `first_name`, `last_name`, `birth_date`, `user_email`, `user_password`, `created_at`)