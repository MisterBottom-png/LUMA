# LUMA Debug Build Delivery

Use this when a LUMA task successfully builds a debug APK.

## Rule

After a successful debug build, upload the built APK to Google Drive.

Preferred command:

```powershell
.\publish-debug-apk.ps1 -Build
```

If the APK has not already been built, build and publish it in one command:

```powershell
.\publish-debug-apk.ps1 -Build
```

## Reporting

In the final report, include the Google Drive link under `Validation run:`.

Use this line:

```text
- google drive debug build link: <url>
```

## Limits

- Only publish generated debug APKs.
- Do not publish release builds, signing keys, source archives, secrets, local databases, or user data.
- Do not claim a Drive link exists unless the upload actually succeeded.
