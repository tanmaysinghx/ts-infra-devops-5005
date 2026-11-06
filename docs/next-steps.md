# Next steps (rollout checklist)

Pending actions to finish rolling out the VPS automation from `feat/vps-automation`. Tick them off as you go.

## Do first (security)
- [ ] **Change the VPS password.** It was shared in plain text: `ssh ubuntu@51.79.164.222 passwd`
- [ ] Ports **8090 (portal-sso)** and **8098 (eggless)** are open to the internet on the current server, because Docker-published ports bypass UFW. They close once those two jobs are redeployed from this branch's pipelines, which bind `127.0.0.1` by default.

## Git / GitHub
- [ ] Push the branch: `git push -u origin feat/vps-automation`
- [ ] Open a PR into `main` and squash-merge it once CI is green
- [ ] Delete the old merged branch: `git push origin --delete ccr-917ae76a-00ueqw`
- [ ] GitHub → *Settings → General*: allow **squash merging** only and enable **Automatically delete head branches**
- [ ] GitHub → *Settings → Rules → Rulesets* on `main`: require a PR and the `CI` checks, and block force pushes and deletions

## Secrets
- [ ] Create the vault password file: `~/.ansible/ts-infra-vault-pass` (`chmod 600`)
- [ ] `cp infra/ansible/inventories/prod/group_vars/all/secrets.yml.example …/secrets.yml` and fill in the admin password, Docker Hub token, GitHub token, `infra-vault-pwd` and the `.env` files
- [ ] `ansible-vault encrypt secrets.yml --vault-password-file ~/.ansible/ts-infra-vault-pass`, then commit the **encrypted** file

## Terraform / Cloudflare
- [ ] Create a Cloudflare API token with **Zone → DNS → Edit** on `tanmaysinghx.com` and `export TF_VAR_cloudflare_api_token=…`
- [ ] `cp infra/terraform/environments/prod/terraform.tfvars.example terraform.tfvars` and set `server_ip`
- [ ] `make init`
- [ ] Import the existing DNS records (jenkins, portal, eggless, vpn, selfie), or delete them in Cloudflare first. The command is in the README under *New VPS from scratch*.

## Apply to the current server
- [ ] `make bootstrap IP=51.79.164.222`: authorizes your SSH key. **Required before apply**, because the playbook turns off password login.
- [ ] `make check`: dry run. Review the diff.
- [ ] `make apply`
- [ ] Verify that https://jenkins.tanmaysinghx.com logs in with the new admin password and that the jobs and credentials are there
- [ ] Redeploy portal-sso and eggless from Jenkins so they bind to `127.0.0.1`

## Later / follow-ups
- [ ] Bind the QA promotion pipelines and the `ts-*` services (`QA_PORT`, 1625, 1606, 1701) to `127.0.0.1` and put them behind nginx
- [ ] hidenseek-agent: drop the binary in `infra/ansible/roles/hidenseek_agent/files/` and set `hidenseek_agent_enabled: true`
- [ ] Move Terraform state to a remote backend (R2/S3 block in `infra/terraform/environments/prod/versions.tf`)
- [ ] Optional: remove the unused host JDK on the current VPS (`openjdk-25-jdk`), since Jenkins runs in Docker
