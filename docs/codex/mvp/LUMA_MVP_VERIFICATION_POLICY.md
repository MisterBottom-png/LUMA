# LUMA MVP verification policy

## Core sequence

```text
claim or backlog entry
-> inspect current revision
-> reproduce or trace
-> classify status
-> preserve if working
-> repair only if broken, partial, missing, or blocking
-> validate
-> record evidence
```

## Evidence preference

Strongest to weakest:

1. Reproducible behavior plus automated test
2. Automated test or build evidence tied to the behavior
3. Direct source/data-flow trace with exact files and symbols
4. Screenshot or manual observation with clear steps
5. Historical document or prior completion claim
6. Assumption

Do not convert historical claims into `verified` without current evidence.

## Audit table

```text
| Area | Status | Evidence | Changed? | Manual check |
```

## Verification-only mode

An MVP audit must not edit application code. Documentation may be updated only to record evidence and status.

## Repair mode

A repair run handles one coherent batch. It does not silently proceed to the next backlog item.

## Device checks

Record device/API/build variant and exact manual steps when emulator/device verification is needed. Do not claim visual, notification, restart, or permission behavior was checked when it was not.
