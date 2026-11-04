# REST Contract — Coupon Claiming (Save Coupon)

**Jira Subtask:** [BE] APG-XX
**Traceability:** RF-02 · RN-10 · RN-11 · RN-12 · RN-13

---

## POST /api/coupons

### Description

Allows a customer to claim an active promotion, creating an UNUSED coupon
linked to their profile. A unique UUID hash is generated for each coupon
to enable QR-based redemption at the restaurant.

### Request Body

```json
{
  "userId": 3,
  "promotionId": 9
}
```

Both fields are required. The server resolves all other data
(restaurant, discount details) through database relationships.

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
- `coupon_hash` is auto-generated (UUID v4, unique per coupon)

### Error Responses

| Code | Scenario | Message | Rule |
|------|----------|---------|------|
| 400  | `userId` missing or null | "The userId field is required." | — |
| 400  | `promotionId` missing or null | "The promotionId field is required." | — |
| 400  | Malformed JSON or empty body | "El cuerpo de la petición es inválido o está vacío." | — |
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

Customer only — validated via JWT bearer token (implementation deferred
to the security sprint). During Sprint 1, the endpoint is open for
functional testing.

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
| GC-01 | Valid userId + promotionId, active promotion | 201 Created with couponHash | Pending |
| GC-02 | Non-existent userId | 404 Not Found | Pending |
| GC-03 | Non-existent promotionId | 404 Not Found | Pending |
| GC-04 | Promotion with status Finished or Suspended | 409 Conflict | Pending |
| GC-05 | Promotion not started yet | 409 Conflict | Pending |
| GC-06 | Promotion already expired | 409 Conflict | Pending |
| GC-07 | Duplicate claim (UNUSED coupon already exists) | 409 Conflict | Pending |
| GC-08 | Missing or empty request body | 400 Bad Request | Pending |
| GC-09 | couponHash is unique UUID v4 format | Validate format | Pending |

### Data Model Dependencies

This contract operates on:
- `coupon` (coupon_id, user_id, discount_id, coupon_status, coupon_hash, coupon_claim_timestamp)
- `discounts` (discount_id, discount_status, discount_start_timestamp, discount_end_timestamp)
- `users` (user_id)
