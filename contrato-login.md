# REST Contract — User Login & JWT Issuance

**Jira Subtask:** [BE] APG-52  
**Traceability:** AUTH-01 · PO-01 · RN-17 · RN-18

---

## POST /api/auth/login

### Description

Authenticates a user using their email and password. Upon successful verification, issues a signed JSON Web Token (JWT) containing identifiable claims and a configurable expiration period.

---

### Request Body

`{`  
  `"email": "carlos.perez@example.com",`  
  `"password": "Password123!"`  
`}`  

Both fields are required.
### Field Constraints

| Field | Type | Required | Constraints   |
| :---- | :---- | :---- | :---- |
| **email** | String | Yes | Valid email format, non-blank |
| **password** | String | Yes | Non-blank |

Success Response
HTTP 200 OK

`{`  
  `"token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsImVtYWlsIjoiY2FybG9zLnBlcmV6QGV4YW1wbGUuY29tIiwiaWF0IjoxNzYyNDYzMDAwLCJleHAiOjE3NjI0OTkwMDB9...",`  
  `"tokenType": "Bearer",`  
  `"expiresIn": 86400,`  
  `"userId": 12,`  
  `"email": "carlos.perez@example.com",`  
  `"firstName": "Carlos",`  
  `"lastName": "Pérez",`  
  `"message": "Authentication successful."`  
`}`
### Error Responses

| Code | Scenario | Message | Rule   |
| :---- | :---- | :---- | :---- |
| **400** | Missing *email* or *password* field | "The field {field} is required." | — |
| **400** | Invalid email syntax | "Invalid email format." | RN-14 |
| **401** | User not found OR invalid password | "Invalid credentials." | RN-17 |
| **400** | Malformed JSON body | "El cuerpo de la petición es inválido o está vacío." | — |

**Security Note (RN-17):** Error messages for authentication failures must remain generic ("Invalid credentials") to prevent user enumeration attacks.  
Business Rules

| Rule | Description   |
| :---- | :---- |
| **RN-17** | Generic Failure Message: Bad password or unregistered email must return HTTP 401 with a generic error message without exposing whether the email exists. |
| **RN-18** | JWT Issuance: A valid login generates a signed JWT (HS256) containing *sub* (userId) and *email*, using the secret key loaded from environment variables (*JWT\_SECRET*). |

### QA Cross-Reference (Test Cases)

| Test Case | Scenario | Expected Result | Status   |
| :---- | :---- | :---- | :---- |
| **CP-08** | Valid credentials (registered email and correct password) | HTTP 200 OK with JWT token, expiration time, and user info | Pending |
| **CP-09** | Non-existent email address | HTTP 401 Unauthorized with generic message "Invalid credentials" | Pending |
| **CP-10** | Registered email with incorrect password | HTTP 401 Unauthorized with generic message "Invalid credentials" | Pending |
| **CP-11** | Missing required fields (blank *email* or *password*) | HTTP 400 Bad Request | Pending |
| **CP-12** | Verification of JWT signature and expiration claim | Token successfully decoded using system *JWT\_SECRET* | Pending |
| **CP-13** | Authentication attempt with tampered JWT token signature | HTTP 401 Unauthorized upon access to protected route | Pending |
| **CP-14** | Authentication attempt with expired JWT token | HTTP 401 Unauthorized upon access to protected route | Pending |

### Data Model Dependencies
This contract operates on:

> * users (user\_id, user\_email, user\_password\_hash, user\_first\_name, user\_last\_name)