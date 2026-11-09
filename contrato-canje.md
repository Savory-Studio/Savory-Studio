# REST Contract — Coupon Redemption Validation

**Jira Subtask:** [BE] APG-35 · APG-38 · [INT] APG-71
**Traceability:** RN-01 · RN-02 · RN-03 · RN-04 · RN-05 · CA-01 · CA-02 · CA-03

---

## POST /api/canjes

### Description

Validates and registers the redemption of a previously claimed coupon at the
restaurant premises using either its QR UUID (`couponHash`) or numeric ID (`couponId`).
This is the core transactional endpoint of the MVP.

### Request Body

```json
{
  "couponId": 2,
  "couponHash": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

At least one of `couponHash` or `couponId` is required (`couponHash` takes priority when both are sent).
User, discount and restaurant data is resolved through the `coupon` → `discounts` → `restaurants`
relationship chain in the database.

### Success Response

**HTTP 201 Created**

```json
{
  "couponId": 2,
  "couponHash": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "USED",
  "redemptionTimestamp": "2026-10-20T14:35:00",
  "message": "Cupón canjeado exitosamente."
}
```

Side effects on success:
- `coupon.coupon_status` transitions from `UNUSED` → `USED`
- `coupon.coupon_redemption_timestamp` is set to the current server time

### Error Responses

| Code | Scenario | Message | Rule |
|------|----------|---------|------|
| 400  | Neither `couponId` nor `couponHash` is provided | "Debe proporcionar couponId o couponHash." | — |
| 401  | Missing, invalid, or expired Bearer token | "The Bearer token is missing. Send Authorization: Bearer <token>." / "The Bearer token is invalid or expired." | RN-18 |
| 404  | `couponHash` or `couponId` does not exist in database | "Cupón no encontrado." | — |
| 409  | `coupon_status` is already `USED` | "Este cupón ya fue utilizado." | RN-01 |
| 409  | `discount_status` is `Finished` or `Suspended` | "Esta promoción ya no está disponible." | RN-02 |
| 409  | `discount_end_timestamp` < current time | "Esta promoción ha vencido." | RN-03 |
| 409  | User's USED count ≥ `discount_max_use_amount_per_user` | "Has alcanzado el límite de usos de esta promoción." | RN-04 |
| 409  | Total USED count ≥ `discount_max_uses` | "Esta promoción se ha agotado." | RN-05 |

### No-Modification Guarantee

When any error response is returned, the database state remains unchanged.
No coupon status transitions, no timestamp writes, and no metric increments
occur. This is verified by QA test case CP-03.

### Authorization

Protected — requires `Authorization: Bearer <token>` header containing a valid signed HS256 JWT token issued by `POST /api/auth/login` (or `TEST_AUTH_TOKEN` in local test environments).

### Business Rules

| Rule  | Description |
|-------|-------------|
| RN-01 | A coupon with status `USED` cannot be redeemed again. |
| RN-02 | A discount with status `Finished` or `Suspended` rejects all redemptions. |
| RN-03 | A discount past its `discount_end_timestamp` rejects redemptions. |
| RN-04 | A user cannot exceed `discount_max_use_amount_per_user` redemptions per discount. |
| RN-05 | Total redemptions cannot exceed `discount_max_uses` for a discount. |

### QA Cross-Reference

| Test Case | Scenario | Expected | Status |
|-----------|----------|----------|--------|
| CP-01     | Valid coupon, active discount, within limits | 201 Created | Aligned ✅ |
| CP-02     | Coupon already USED or discount expired | 409 Conflict | Aligned ✅ |
| CP-03     | After CP-02 rejection, DB unchanged | No side effects | Aligned ✅ |
| CP-04     | Invalid or nonexistent couponId / couponHash | 400 / 404 | Aligned ✅ |

### Data Model Dependencies

This contract operates on:
- `coupon` (coupon_id, coupon_hash, coupon_status, coupon_redemption_timestamp)
- `discounts` (discount_status, discount_end_timestamp, discount_max_uses, discount_max_use_amount_per_user)
- `users` (user_id — resolved via coupon.user_id)
- `restaurants` (restaurant_id — resolved via discounts.discount_restaurant_id)
