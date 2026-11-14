# REST Contract — Authenticated User Profile Consultation & Update

**Jira Subtask:** [BE] APG-54
**Traceability:** User Profile Management · JWT Ownership Isolation

---

## GET /api/users/me

### Description

Retrieves the profile information of the currently authenticated user. The user identifier is extracted exclusively from the validated JWT session (`SecurityContextHolder`), ensuring that sensitive credentials (`password_hash`) are never exposed and users can only view their own profile.

### Success Response

**HTTP 200 OK**

```json
{
  "userId": 10,
  "firstName": "Jacobo",
  "secondName": "Andrés",
  "lastName": "Arroyave",
  "birthday": "2005-09-24",
  "joinDate": "2026-11-06T16:56:36.438584",
  "email": "arroyaveperezjacobo@gmail.com"
}
```

**Fields explicitly excluded from the response:**
- `passwordHash` — internal credential hash never exposed via API

### Error Responses

| Code | Scenario | Message | Rule |
|------|----------|---------|------|
| 401  | Missing, invalid, or expired Bearer token | "The Bearer token is missing. Send Authorization: Bearer <token>." / "No se pudo identificar al usuario autenticado." | Auth |
| 404  | Authenticated user ID does not exist in database | "Usuario autenticado no encontrado." | — |

---

## PATCH /api/users/me

### Description

Allows the currently authenticated user to partially update their editable profile fields. The target record is resolved strictly from the JWT token (`userId`), preventing cross-user modifications. Any attempt to include non-editable or restricted system fields (`userId`, `passwordHash`, `joinDate`, etc.) is rejected with `400 Bad Request`.

### Request Body

```json
{
  "firstName": "Jacobo",
  "secondName": "Andrés",
  "lastName": "Arroyave",
  "birthday": "2005-09-24",
  "email": "arroyaveperezjacobo@gmail.com"
}
```

**Fields NOT accepted in the request (rejected with `400 Bad Request` if sent):**
- `userId` — immutable primary key resolved from JWT
- `passwordHash` — sensitive credential field protected from direct profile edits
- `joinDate` — immutable registration timestamp set at account creation
- Any other unrecognized property outside the allowed whitelist

### Field Constraints

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `firstName` | String | No (at least 1 allowed field required) | Must be a non-blank string with length `1..100` if provided |
| `secondName` | String | No | Optional middle name; length `<= 100`. Empty/blank strings normalize to `null` |
| `lastName` | String | No | Must be a non-blank string with length `1..100` if provided |
| `birthday` | Date (`YYYY-MM-DD`) | No | Must be a valid ISO date strictly in the past (`birthday < TODAY`) |
| `email` | String | No | Must match valid email format (`<= 150` chars), normalized to lowercase, and unique across `users` |

### Success Response

**HTTP 200 OK**

```json
{
  "userId": 10,
  "firstName": "Jacobo",
  "secondName": "Andrés",
  "lastName": "Arroyave",
  "birthday": "2005-09-24",
  "joinDate": "2026-11-06T16:56:36.438584",
  "email": "arroyaveperezjacobo@gmail.com"
}
```

Side effects on success:
- Updates only the provided editable columns in the `users` table for the authenticated `user_id`
- Trims leading/trailing whitespace on string fields and normalizes `email` to lowercase

### Error Responses

| Code | Scenario | Message | Rule |
|------|----------|---------|------|
| 400  | Empty JSON object `{}` with no editable fields | "Debe proporcionar al menos un campo editable para actualizar (firstName, secondName, lastName, birthday o email)." | — |
| 400  | Forbidden or non-editable field included (e.g., `userId`, `passwordHash`, `joinDate`) | "Los siguientes campos no están permitidos o no son editables: {fields}." | Security |
| 400  | `firstName` or `lastName` is blank or exceeds 100 characters | "El primer nombre no puede estar vacío." / "El apellido no puede superar los 100 caracteres." | — |
| 400  | `birthday` is invalid format or not in the past | "El formato de fecha de nacimiento debe ser YYYY-MM-DD." / "La fecha de nacimiento debe ser una fecha pasada." | — |
| 400  | `email` is blank or has invalid format | "El formato del correo electrónico no es válido." | — |
| 400  | Field value has invalid JSON data type (not a string) | "El campo '{fieldName}' debe ser una cadena de texto." | — |
| 400  | Malformed JSON body | "El cuerpo de la petición es inválido o está vacío." | — |
| 401  | Missing, invalid, or expired Bearer token | "The Bearer token is missing. Send Authorization: Bearer <token>." / "No se pudo identificar al usuario autenticado." | Auth |
| 404  | Authenticated user ID does not exist in database | "Usuario autenticado no encontrado." | — |
| 409  | `email` already belongs to another registered user | "El correo electrónico ya está registrado por otro usuario." | Unique Email |

---

### Authorization

Protected — both `GET /api/users/me` and `PATCH /api/users/me` require the `Authorization: Bearer <token>` header containing a valid signed HS256 JWT token issued by `POST /api/auth/login` (or `TEST_AUTH_TOKEN` in local test environments). Ownership is enforced automatically via `SecurityUtils.getCurrentUserId()`, making it impossible for one user to read or modify another user's profile.

---

### Business & Security Rules

| Rule | Description |
|------|-------------|
| Ownership Isolation | The target `user_id` is always derived from the authenticated JWT principal, never from path parameters or request body fields. |
| Credential Protection | `password_hash` is excluded from `UserProfileResponse` and strictly rejected if passed in `PATCH /api/users/me`. |
| Whitelist Enforcement | Only `firstName`, `secondName`, `lastName`, `birthday`, and `email` are permitted in partial updates; any extra property triggers `400 Bad Request`. |
| Email Uniqueness | Updating `email` requires verifying that no other account in `users` already uses that normalized email address (`409 Conflict`). |

---

### QA Cross-Reference

| Test Case | Scenario | Expected | Status |
|-----------|----------|----------|--------|
| UP-01 | `GET /api/users/me` with valid Bearer token returns profile without `passwordHash` | 200 OK | Tested |
| UP-02 | `GET /api/users/me` or `PATCH /api/users/me` without Authorization header | 401 Unauthorized | Tested |
| UP-03 | `PATCH /api/users/me` with valid partial update (`firstName`, `secondName`) | 200 OK | Tested |
| UP-04 | `PATCH /api/users/me` with empty body `{}` | 400 Bad Request | Tested |
| UP-05 | `PATCH /api/users/me` with blank `firstName` or `lastName` | 400 Bad Request | Tested |
| UP-06 | `PATCH /api/users/me` with invalid `email` format or future `birthday` | 400 Bad Request | Tested |
| UP-07 | `PATCH /api/users/me` attempting to inject `userId` or `passwordHash` | 400 Bad Request | Tested |
| UP-08 | `PATCH /api/users/me` with an `email` already registered by another user | 409 Conflict | Tested |

---

### Data Model Dependencies

This contract operates on:
- `users` (`user_id`, `user_first_name`, `user_second_name`, `user_last_name`, `user_birthday`, `user_join_date`, `user_email`, `user_password_hash` — read/update restricted to authenticated owner; `user_password_hash` never exposed or modified)