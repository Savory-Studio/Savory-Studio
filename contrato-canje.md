# REST Contract — Coupon Redemption Validation

**Jira Subtask:** [BE] APG-35 · APG-38 · APG-65 · [INT] APG-71
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
| CP-05     | N simultaneous redemptions of the same coupon | 1× 201, N-1× 409 (RN-01) | Automated (APG-65) |
| CP-06     | Simultaneous redemptions of different coupons beyond `max_uses` | Exactly `max_uses` × 201, rest 409 (RN-05) | Automated (APG-65) |
| CP-07     | Simultaneous redemptions by one user beyond `max_use_amount_per_user` | Exactly the limit × 201, rest 409 (RN-04) | Automated (APG-65) |
| CP-08     | Rejected concurrent redemptions | Data unchanged, original `redemption_timestamp` preserved | Automated (APG-65) |

### Concurrency Guarantees (APG-65)

`@Transactional` alone does not stop two simultaneous requests from reading the same state (both would see
`UNUSED` and a free slot, and both would succeed). `CanjeService.redeem` therefore takes PostgreSQL row locks
(`SELECT ... FOR UPDATE`, JPA `PESSIMISTIC_WRITE`) **before** evaluating RN-01 to RN-05:

| Order | Locked row | Repository method | What it protects |
|-------|------------|-------------------|------------------|
| 1 | `coupon` (the coupon being redeemed) | `CouponRepository.findByIdForUpdate` / `findByCouponHashForUpdate` | RN-01: concurrent redemptions of the **same coupon** are serialized; the loser re-reads the committed `USED` state and gets `409`. No lost update of `coupon_redemption_timestamp`. |
| 2 | `discounts` (the coupon's promotion) | `DiscountRepository.findByIdForUpdate` | RN-04 / RN-05: redemptions of **different coupons of the same promotion** are serialized, so the `USED` counts are read after the previous redemption committed. |

Properties:
- **Single winner:** N simultaneous requests for one coupon produce exactly one `201` and N-1 `409`.
- **Limits are never exceeded:** with `discount_max_uses = M` (or `discount_max_use_amount_per_user = M`) at most `M`
  coupons end up `USED`; the remaining requests receive `409` (`"Esta promoción se ha agotado."` /
  `"Has alcanzado el límite de usos de esta promoción."`).
- **Rejected requests do not modify data:** the exception rolls the transaction back and releases the locks.
- **No deadlocks:** locks are always acquired in the same order (coupon, then discount), and no other code path
  holds a discount lock while waiting for a coupon lock.
- **Scope:** only redemptions of the same promotion wait for each other; other promotions are not affected.
  Claiming (`POST /api/coupons`) does not take these locks and is guarded by the partial unique index
  `uq_unused_coupon_per_user_promotion`.
- The usage counts are still derived (`COUNT` of `USED` coupons); no counter column is introduced.

Automated verification:

| Test | Scope | Scenario |
|------|-------|----------|
| `CanjeServiceTest` | Unit (Mockito) | Locked lookups are used (id and hash), lock order coupon → discount, RN-01 to RN-05 messages preserved, nothing is saved on rejection, contextual authorization preserved. |
| `CanjeConcurrencyIntegrationTest` | PostgreSQL (Testcontainers), full HTTP stack with JWT | 8 simultaneous requests: same coupon by id (1×`201`, 7×`409`, persisted timestamp equals the winner's), same coupon by hash, 8 coupons of a promotion with `max_uses = 3` (3×`201`, 5×`409`), one user with 5 coupons and `max_use_amount_per_user = 2` (2×`201`, 3×`409`), and repeated attempts on an already `USED` coupon leave it untouched. |

Run them locally (Docker must be running):

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CanjeServiceTest,CanjeConcurrencyIntegrationTest" test
```

### Data Model Dependencies

This contract operates on:
- `coupon` (coupon_id, coupon_hash, coupon_status, coupon_redemption_timestamp)
- `discounts` (discount_status, discount_end_timestamp, discount_max_uses, discount_max_use_amount_per_user)
- `users` (user_id — resolved via coupon.user_id)
- `restaurants` (restaurant_id — resolved via discounts.discount_restaurant_id)
