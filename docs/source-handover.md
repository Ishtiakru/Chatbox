# Source handover: review-only preparation

This is **not deployed or automatically updating**. Do not enable it until the access model, Drive permissions, backend, App Check and publishing process have been reviewed and verified. No actual source URL or QR is embedded in the application.

## Access and QR

The optional `getSourceHandover` callable uses the existing server-side `system/admin` owner record and App Check. It reads `system/sourceHandover` through the Admin SDK and returns a configured Drive URL and optional 40-character commit. The optional admin UI (`VITE_ENABLE_ADMIN_BACKEND=true`, `VITE_ENABLE_SOURCE_HANDOVER=true`, and configured App Check) generates a QR locally in memory after an explicit action. The Spark admin prototype is intentionally not connected. The UI checks are convenience only; the callable authorization and Drive permissions are the security boundaries. A prior recipient can retain a screenshot or URL.

Set `system/sourceHandover` only through a trusted server/Admin SDK after reviewing the deployed Firestore rules; deny all client reads/writes to this document. Set its `url` to a private Drive file's `https://drive.google.com/file/d/.../view` URL and `commit` to the commit the archive actually contains. Never configure a public file if confidentiality is required. Grant Drive access only to the owner and explicitly approved developers; a QR alone does not grant them access. Removing access cannot recall downloads already made.

Existing backend functionality, including `system/admin`, is an undeployed experimental prototype. Firebase Functions, App Check (including Android native App Check), credentials, billing eligibility, server-side administrator provisioning and deployment still require separate setup. The isolated test APK currently does not enable this feature. Do not put a service credential, public-link archive, token, or signing key in the app bundle or repository.

## Producing archives

From a full, non-shallow Git repository on the chosen branch:

```
python3 scripts/export-source-handover.py /secure/location/Chatbox-source.zip
```

The script exports the committed tree and the branch's complete reachable Git history; uncommitted changes, ignored files, databases, external assets and secrets are excluded. Keep independent backups. Review the archive before sharing, including old history. A full history can include old secrets even if they are absent from HEAD. Removing a secret from a later commit does not remove it from history.

The existing Drive archive is a one-time snapshot. Its anyone-with-link permission was removed at the owner's request; authorized accounts must be granted individual access. Copies downloaded while it was public cannot be recalled. Automatic refresh has **not** been configured. `.github/workflows/source-handover-publisher.yml` and `scripts/publish-source-handover.py` are a disabled, untested preparation for replacing the contents of the same private Drive file, not a deployed service. The job is restricted to the session branch, serializes its own runs and checks the remote branch head before upload. A push can still happen between this check and the upload; a subsequent successful run is needed for eventual freshness. Workflow failures, skipped runs, branch deletion, service changes or lost access leave an older archive. Monitor runs; no guaranteed alerting has been set up. Edits kept on another device or branch do not appear automatically.

Before any activation, an operator must review the entire reachable history and protect secrets, restrict the Drive file to explicitly approved users, review any group and inherited/shared-drive access, and separately validate the exporter and publisher in an isolated test environment. The publisher refuses `anyone`, `domain`, unknown, or unlistable permissions but that check is not a complete audit and sharing can change after the check. Create a dedicated Workload Identity Federation configuration scoped to this repository, branch and publisher workflow; do not broaden the existing test-project reader. Configure its service account with the minimum Drive access needed for the one existing file and enable the Drive API. Configure GitHub repository variables `CHATBOX_SOURCE_WIF_PROVIDER`, `CHATBOX_SOURCE_SERVICE_ACCOUNT` and `CHATBOX_SOURCE_DRIVE_FILE_ID`; after review, `CHATBOX_SOURCE_PUBLISH_ENABLED=true` opts in. Do not place access tokens, private keys or credentials in the repository. Any authorized branch writer who can change the workflow may also affect publishing; apply branch protection and review. Drive/Cloud/GitHub service availability, retention, usage limits and potential charges remain the operator's responsibility.

The publisher confirms Drive's returned file ID and MD5 content checksum, but it **does not update** `system/sourceHandover.commit`. The admin UI labels that commit as potentially outdated; reconcile this trusted record separately after a confirmed upload. The destination permissions check and the branch-head check are not atomic with the upload. Review privacy, permissions, retention, monitoring, costs and failure handling before enabling any automation.

The existing QR image was previously shared publicly; it reveals the Drive link but does not itself grant access to the now-restricted archive. Each developer needs explicit Drive permission. Neither restriction nor a new QR can retract copies already downloaded.
