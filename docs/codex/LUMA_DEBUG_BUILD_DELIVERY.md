# LUMA Debug Build Delivery

Use this when a LUMA task successfully builds a debug APK.

## Rule

After a successful debug build, provide a temporary way for the user to download the built APK.

Preferred command:

```powershell
.\publish-debug-apk.ps1 -Upload
```

If network upload is blocked or not approved, provide a local temporary download instead:

```powershell
.\publish-debug-apk.ps1 -ServeLocal
```

If the APK has not already been built, build and publish it in one command:

```powershell
.\publish-debug-apk.ps1 -Build -Upload
```

## Reporting

In the final report, include the link or fallback path under `Validation run:`.

Use one of these lines:

```text
- temporary debug build download: <url>
- local debug build download: http://localhost:<port>/orbit-debug.apk
- debug APK path: <path> (temporary upload unavailable: <reason>)
```

## Limits

- Only publish generated debug APKs.
- Do not publish release builds, signing keys, source archives, secrets, local databases, or user data.
- Do not claim a temporary download exists unless the upload or local server actually started.
- If upload requires network approval and approval is unavailable, report the local fallback clearly.
