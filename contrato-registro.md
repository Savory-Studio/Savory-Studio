# REST Contract — User Registration

**Jira Subtask:** [BE] APG-51
**Traceability:** APG-46 · APG-51

---

## POST /api/auth/register

### Description

Allows a new user to register an account by providing their personal information, unique email, and password. The password is hashed using **BCrypt** before being stored. This endpoint is dedicated solely to user creation; it does not issue JWT tokens or handle user login.

### Request Body

```json
{
  "firstName": "Carlos",
  "lastName": "Pérez",
  "birthday": "1995-05-15",
  "email": "carlos.perez@example.com",
  "password": "Password123!"
}
```

All fields shown are required. An optional `secondName` can be supplied. The request also accepts `birthDate` as an alias for `birthday`.

### Field Constraints

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `firstName` | String | Yes | Non-blank, max 50 characters |
| `secondName` | String | No | Maximum 50 characters |
| `lastName` | String | Yes | Non-blank, max 50 characters |
| `birthday` | LocalDate | Yes | Format `YYYY-MM-DD`, valid date |
| `email` | String | Yes | Valid email syntax, unique, maximum 255 characters |
| `password` | String | Yes | Minimum 8 characters, maximum 72 UTF-8 bytes, hashed with BCrypt prior to storage |

### Success Response

**HTTP 201 Created**

```json
{
  "id": 12,
  "firstName": "Carlos",
  "secondName": null,
  "lastName": "Pérez",
  "birthday": "1995-05-15",
  "email": "carlos.perez@example.com",
  "joinDate": "2026-11-04T19:00:00"
}
```

Side effects on success:
- A new row is inserted in the `users` table.
- The password is saved **exclusively as a BCrypt hash** and is never returned in the response.
- `user_join_date` is set to the current server timestamp.

---

### Error Responses

| Code | Scenario | Message |
|------|----------|---------|
| 400 | Required field missing or blank (`firstName`, `email`, etc.) | "El campo {field} es obligatorio." |
| 400 | Invalid email syntax | "El correo electrónico no tiene un formato válido." |
| 400 | Password too short (less than 8 characters) | "La contraseña debe tener al menos 8 caracteres." |
| 400 | Malformed JSON or unreadable body | "El cuerpo de la petición es inválido o está vacío." |
| 409 | Email is already registered | "Email address is already registered." |

---

### No-Modification Guarantee

A rejected registration does not persist a user.

---

### Authorization

Public — Endpoint is open and does not require authentication headers (`SimulatedAuthFilter`).

---

### QA Cross-Reference

| Test Case | Scenario | Expected | Status |
|-----------|----------|----------|--------|
| CP-01     | Valid registration data and non-existent email | 201 Created without password in response | Automated |
| CP-02     | Missing required field (e.g., `email` or `password`) | 400 Bad Request | Automated |
| CP-03     | Invalid email syntax (e.g., "invalid-email") | 400 Bad Request | Automated |
| CP-04     | Password with fewer than 8 characters | 400 Bad Request | Automated |
| CP-05     | Registration attempt with an existing email | 409 Conflict | Automated |
| CP-06     | Database verification: confirm stored password is a `$2a$` hash | BCrypt hash confirmed in DB | Automated |
| CP-07     | Malformed or empty request body | 400 Bad Request | Automated |

---

### Data Model Dependencies

This contract operates on:
- `users` (`user_id`, `user_first_name`, `user_second_name`, `user_last_name`, `user_birthday`, `user_email`, `user_password_hash`, `user_join_date`)
