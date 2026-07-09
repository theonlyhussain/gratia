# Git Workflow Rules

- `main` = stable public releases only.
- `beta` = all daily development and testing.
- Do not create feature branches for now.
- All development happens on `beta`.
- Every APK tested comes from `beta`.
- GitHub Releases for beta builds should be marked as "Pre-release".
- Stable releases only come from `main`.
- Never merge `beta` into `main` automatically.
- Only merge after the user explicitly says: "Yes".

If the user says:
- "Yes" → Merge `beta` into `main`, push `main`, create a stable tag and release.
- "Semi work" → Stay on `beta`, continue fixing bugs.
- "No" → Stay on `beta`, continue fixing bugs.

Do not delete `beta`. It is the permanent development branch.
