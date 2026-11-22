# Isolated Firebase access through GitHub Actions (not configured)

This is an optional, **read-only** first step toward remote work without Cloud Shell. It is not a Cloud Shell session and does not give the agent direct Firebase Console access. No Google access has been granted or checked by adding this workflow. It is restricted to the isolated test project `chatbox-test-2c236731bf` (project number `1012186851732`), not the production project.

A Google Cloud project administrator must first configure Workload Identity Federation and service-account impersonation in that project. This can be done in Google Cloud Console, but may require enabling relevant APIs; enabling APIs and granting IAM roles are consequential changes and may affect billing or quotas. The owner must explicitly approve them. No service-account key or long-lived token is required. Do not paste credentials into chat, commit them, or upload them to Drive.

## Trust requirements

The GitHub OIDC provider must use issuer `https://token.actions.githubusercontent.com` and restrict its attribute condition to the following claims (including the immutable repository and owner IDs). Adapt the attribute mapping to the provider settings; the claims below are illustrative CEL and must be applied at the Google Cloud trust boundary, not merely in workflow code:

```
assertion.repository_id == '1466284145' &&
assertion.repository_owner_id == '359412232' &&
assertion.ref == 'refs/heads/arena/f649c1bd-chatbox' &&
assertion.workflow_ref == 'Ishtiakru/Chatbox/.github/workflows/isolated-firebase-access.yml@refs/heads/arena/f649c1bd-chatbox'
```

Map every claim used by the condition or principal set, as well as the subject: `google.subject=assertion.sub`, `attribute.repository_id=assertion.repository_id`, `attribute.repository_owner_id=assertion.repository_owner_id`, `attribute.ref=assertion.ref`, and `attribute.workflow_ref=assertion.workflow_ref`. Grant `roles/iam.workloadIdentityUser` **on the isolated service account**, only to the principal set for repository ID `1466284145` in this pool. Give that account the minimum read-only project permission needed for `resourcemanager.projects.get` (for example, a reviewed role containing it). Do not grant broad project Owner/Editor roles. Branch and workflow restrictions do not protect against someone who can modify this branch's workflow; restrict who can push and review workflow changes. GitHub repository and workflow access remain security-sensitive.

The owner created a pool `chatbox-github`, provider `github-actions`, and service account `chatbox-github-reader` in the isolated project. The workflow now fixes those **public resource identifiers** directly; no GitHub variables or service-account key are needed for this connection. The provider's complete saved condition and the service account's IAM roles and impersonation grant still need confirmation. The current GitHub integration was denied access to both the Actions secrets and variables APIs; this does not affect the use of these public identifiers.

The workflow only runs for a push on the current branch with `[isolated-firebase-access]` in the commit message, or a manual dispatch where GitHub permits it. GitHub manual dispatch requires the workflow file to exist on the default branch, so it may not be available while this branch is unmerged. It reads project metadata using a short-lived credential; it does not deploy, enable APIs, change IAM, read chat data, or grant new access. The owner reported saving the test-project service-account impersonation grant, but a successful connection and the service account's project role have not yet been confirmed. Running it requires separate owner approval. For such a dedicated connection run, use an empty commit: the marker also skips the official emulator and Android smoke workflows, so never use it for code changes. The read-only connection run is not an APK build or release/security validation.

Once the connection exists, any proposed write (such as changing test Firebase rules) needs separately reviewed least-privilege IAM and an explicit request. The workflow as supplied cannot edit Firebase. Production requires a separate review and authorization; never reuse this test-project trust for production.
