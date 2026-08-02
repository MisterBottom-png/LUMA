# Emulator verification

The supported local verification target is the named `luma_api_36` AVD using an API 36 Google APIs x86_64 image. The scripts discover only emulator serials and verify the AVD name before interacting; they do not select an attached physical device.

Typical flow:

```powershell
scripts\setup-android-emulator.ps1 -ApiLevel 36 -AvdName luma_api_36
scripts\start-android-emulator.ps1 -AvdName luma_api_36 -Headless
scripts\wait-for-emulator.ps1 -AvdName luma_api_36
scripts\verify-emulator.ps1 -AvdName luma_api_36 -ExpectedApiLevel 36
scripts\run-device-verification.ps1 -AvdName luma_api_36 -ApiLevel 36
```

The wait script accepts an empty `init.svc.bootanim` value on API 36 only when `sys.boot_completed` is `1` and Package Manager is ready. This reflects the current image behavior while retaining independent readiness checks.
