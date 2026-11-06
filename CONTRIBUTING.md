# Contributing

## Branching (trunk-based)

`main` is the only long-lived branch. It is what Jenkins checks out (`*/main`) and what Ansible deploys, so it must always be releasable.

| Branch | Use |
|---|---|
| `main` | Protected. Changes land only through a pull request with green CI. |
| `feat/<short-name>` | New pipeline, role, or capability |
| `fix/<short-name>` | Bug fix |
| `chore/<short-name>` · `docs/<short-name>` · `ci/<short-name>` | Housekeeping |

Short-lived branches only: open a PR early, **squash-merge**, and let GitHub delete the branch on merge. Environments (`dev`, `qa`, `prod`) are **directories**, not branches: `environments/<env>/` for app config, `infra/ansible/inventories/<env>/` and `infra/terraform/environments/<env>/` for servers.

### Recommended GitHub settings
- *Settings → General*: allow **squash merging** only and enable **Automatically delete head branches**.
- *Settings → Rules → Rulesets* on `main`: require a pull request, require the `CI` status checks, and block force pushes and deletions.

## Commit messages — [Conventional Commits](https://www.conventionalcommits.org/)

```
<type>(<scope>): <summary in imperative mood>
```
- **type**: `feat`, `fix`, `chore`, `docs`, `refactor`, `ci`, `perf`
- **scope**: an app (`portal-sso`, `selfie-backend`), or `infra`, `ansible`, `terraform`, `jenkins`, `nginx`, `pipelines`

Examples: `feat(pipelines): add opsnow QA promotion`, `fix(nginx): raise jenkins upload limit`.

## Before opening a PR
```bash
make lint      # terraform fmt/validate, yamllint, ansible-lint
make check     # if you touched infra: dry-run diff against the server
```

## Secrets
- Never commit plaintext: `.env`, `terraform.tfvars`, `*.tfstate`, unencrypted `secrets.yml`. CI rejects them.
- App runtime config → `environments/<env>/configs/<app>/.env.enc` (`scripts/vault.js`).
- Server/Jenkins secrets → `infra/ansible/inventories/<env>/group_vars/all/secrets.yml` (`ansible-vault`).
