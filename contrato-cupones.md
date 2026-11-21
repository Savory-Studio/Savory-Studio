# REST Contract — Coupon Claiming & Customer Coupon History

**Jira Subtask:** [BE] APG-32 · APG-43 · [INT] APG-71
**Traceability:** RF-02 · RN-10 · RN-11 · RN-12 · RN-13

---

## GET /api/cupones/usuario/{userId} (Alias: `/api/coupons/usuario/{userId}`)

### Description

Retrieves all coupons (`UNUSED` and `USED`) claimed by the specified customer, retaining their full redemption history along with promotion and restaurant details.

### Success Response

**HTTP 200 OK**

```json
[
  {
    "couponId": 15,
    "userId": 10,
    "promotionId": 20,
    "promotionName": "2x1 Lunch",
    "restaurantId": 1,
    "restaurantName": "Savory Grill",
    "type": "IXJ",
    "amount": null,
    "customerTakesAmount": 2,
    "customerPaysAmount": 1,
    "couponHash": "a3f1b2c4-d5e6-7890-abcd-ef1234567890",
    "status": "UNUSED",
    "claimTimestamp": "2026-10-30T14:35:00",
    "redemptionTimestamp": null,
    "endTimestamp": "2026-11-26T23:59:00",
    "imageUrl": "https://example.com/promo.jpg"
  }
]
```

### Error Responses (`GET /api/cupones/usuario/{userId}`)

| Code | Scenario | Message |
|------|----------|---------|
| 400  | `userId <= 0` or non-integer path value | "The userId must be a positive integer." |
| 401  | Missing, invalid, or expired Bearer token | "The Bearer token is missing. Send Authorization: Bearer <token>." / "The Bearer token is invalid or expired." |
| 404  | `userId` does not exist | "User not found." |

---

## POST /api/coupons (Alias: `/api/cupones`)

### Description

Allows an authenticated customer to claim an active promotion, creating an UNUSED coupon
linked to their profile. The claiming customer is identified directly from the authenticated JWT
session (`SecurityContext`), preventing identity spoofing. A unique UUID hash (`coupon_hash`) is generated
for each coupon via `CouponHashGenerator` to enable QR-based redemption at the restaurant and meet Supabase constraints.

### Request Body

```json
{
  "promotionId": 9
}
```

Optional payload with customer confirmation:

```json
{
  "userId": 3,
  "promotionId": 9
}
```

- `promotionId`: required positive integer.
- `userId`: optional integer. If provided, it must match the authenticated JWT user ID; any mismatch is rejected with HTTP 403 Forbidden.

### Success Response

**HTTP 201 Created**

```json
{
  "couponId": 15,
  "userId": 3,
  "promotionId": 9,
  "couponHash": "a3f1b2c4-d5e6-7890-abcd-ef1234567890",
  "status": "UNUSED",
  "claimTimestamp": "2026-10-30T14:35:00",
  "message": "Promoción guardada en el perfil del cliente."
}
```

Side effects on success:
- A new row is inserted in the `coupon` table
- `coupon_status` is set to `UNUSED`
- `coupon_claim_timestamp` is set to the current server time
- `coupon_hash` is auto-generated (UUID v4, unique per coupon via `CouponHashGenerator`, required `NOT NULL` by Supabase)

### Error Responses (`POST /api/coupons`)

| Code | Scenario | Message | Rule |
|------|----------|---------|------|
| 400  | `promotionId` missing or null | "The promotionId field is required." | — |
| 400  | Malformed JSON or empty body | "El cuerpo de la petición es inválido o está vacío." | — |
| 401  | Missing, invalid, or expired Bearer token | "The Bearer token is missing. Send Authorization: Bearer <token>." / "The Bearer token is invalid or expired." | RN-18 |
| 403  | `userId` in body does not match authenticated user | "Acceso denegado: no puede asociar promociones a otro usuario" | Anti-spoofing |
| 404  | `userId` does not exist | "User not found." | — |
| 404  | `promotionId` does not exist | "Promotion not found." | — |
| 409  | `discount_status` is not `Active` | "This promotion is no longer available." | RN-10 |
| 409  | `discount_start_timestamp` > current time | "This promotion is not active yet." | RN-11 |
| 409  | `discount_end_timestamp` < current time | "This promotion has expired." | RN-12 |
| 409  | UNUSED coupon already exists for user+discount | "This promotion is already associated with the customer profile." | RN-13 |

### No-Modification Guarantee

When any error response is returned, the database state remains unchanged.
No coupon is created, no hash is generated, and no timestamp is written.

### Authorization

Protected — requires `Authorization: Bearer <token>` header containing a valid signed HS256 JWT token issued by `POST /api/auth/login` (or `TEST_AUTH_TOKEN` in local test environments). The claiming user ID is derived directly from the authenticated principal in `SecurityContext`.

### Business Rules

| Rule  | Description |
|-------|-------------|
| RN-10 | Only promotions with status `Active` can be claimed. |
| RN-11 | A promotion whose start date is in the future cannot be claimed yet. |
| RN-12 | A promotion past its end date cannot be claimed. |
| RN-13 | A user cannot have more than one UNUSED coupon for the same promotion. |

### QA Cross-Reference

| Test Case | Scenario | Expected | Status |
|-----------|----------|----------|--------|
| GC-01 | Valid JWT + promotionId, active promotion | 201 Created with couponHash and UNUSED status | Automated |
| GC-01b | Valid JWT + matching userId + promotionId | 201 Created with couponHash | Automated |
| GC-01c | Valid JWT + mismatched userId in body | 403 Forbidden (spoofing prevented) | Automated |
| GC-02 | Non-existent userId in auth context | 404 Not Found | Automated |
| GC-03 | Non-existent promotionId | 404 Not Found | Automated |
| GC-04 | Promotion with status Finished or Suspended | 409 Conflict | Automated |
| GC-05 | Promotion not started yet | 409 Conflict | Automated |
| GC-06 | Promotion already expired | 409 Conflict | Automated |
| GC-07 | Duplicate claim (UNUSED coupon already exists) | 409 Conflict | Automated |
| GC-08 | Missing promotionId or empty request body | 400 Bad Request | Automated |
| GC-09 | couponHash is unique UUID v4 format | Validate format | Automated |
| GC-10 | Unauthenticated request without Bearer token | 401 Unauthorized | Automated |

### Data Model Dependencies

This contract operates on:
- `coupon` (coupon_id, user_id, discount_id, coupon_status, coupon_hash, coupon_claim_timestamp, coupon_redemption_timestamp)
- `discounts` (discount_id, discount_status, discount_start_timestamp, discount_end_timestamp)
- `users` (user_id)
