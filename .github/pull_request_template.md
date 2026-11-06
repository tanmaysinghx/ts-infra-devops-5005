## What & why

<!-- One or two sentences. Link the issue if there is one. -->

## Type
- [ ] feat · [ ] fix · [ ] chore · [ ] docs · [ ] refactor · [ ] ci

## Affects
- [ ] Jenkins pipeline(s): <!-- which jobs -->
- [ ] Infra (Terraform / Ansible) — attach `make plan` / `make check` output if it changes the server
- [ ] Environment config (`environments/**`)

## Checklist
- [ ] `make lint` passes
- [ ] No plaintext secrets (`.env`, tokens, unencrypted `secrets.yml`)
- [ ] README / docs updated if behaviour or parameters changed
