# Manager 0.6.0 Release Gate

Status: PREPARED — publication is blocked until RC20 rooted-device regression is accepted.

## Required GitHub Secrets

The release workflow intentionally cannot read or infer whether these exist ahead of time. All four must be configured before a signed build can succeed:

- `MANAGER_RELEASE_KEYSTORE_B64`: base64 of the Android release keystore file.
- `MANAGER_RELEASE_STORE_PASSWORD`: keystore password.
- `MANAGER_RELEASE_KEY_ALIAS`: signing key alias.
- `MANAGER_RELEASE_KEY_PASSWORD`: signing key password.

The keystore itself must never be committed to git.

## Safe release sequence

1. Complete `docs/manager-rc20-regression.md` on the rooted Android 16 target and resolve all P0/P1 findings.
2. Mark RC20 COMPLETE and bump the app to `versionName "0.6.0"` / `versionCode 31`.
3. Run **Release Box & AGH Manager 0.6.0** with `publish=false`.
4. Verify the signed APK on the target device and record its SHA256.
5. Integrate the exact qualified release commit into `feat/agh-manager-apk` while retaining the previous stable SHA as the rollback point.
6. Run the same workflow with `publish=true` and confirmation `RELEASE-0.6.0`.
7. The workflow refuses publication unless the stable branch SHA exactly matches the release source and `v0.6.0` does not already exist.

## Release workflow gates

The workflow runs `:app:test`, `:app:lintRelease` and `:app:assembleRelease`, verifies the APK with Android `apksigner`, generates a SHA256 file, uploads the signed artifact, and only then may create the GitHub Release.

A missing signing secret, wrong version, stable/source SHA mismatch, duplicate tag or failed signature verification stops publication.
