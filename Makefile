# One entry point for infra work. Run `make help`.
SHELL        := /bin/bash
ENV          ?= prod
TF_DIR       := infra/terraform/environments/$(ENV)
ANSIBLE_DIR  := infra/ansible
INVENTORY    := inventories/$(ENV)/hosts.yml
VAULT_FILE   ?= $(HOME)/.ansible/ts-infra-vault-pass
VAULT_ARG    := $(if $(wildcard $(VAULT_FILE)),--vault-password-file $(VAULT_FILE),)
SSH_USER     ?= ubuntu
TAGS         ?=
TAGS_ARG     := $(if $(TAGS),--tags $(TAGS),)

.DEFAULT_GOAL := help
.PHONY: help bootstrap init plan apply provision check dns lint fmt vault-edit vault-view

help: ## Show targets
	@awk 'BEGIN{FS=":.*## "} /^[a-zA-Z_-]+:.*## /{printf "  \033[36m%-12s\033[0m %s\n",$$1,$$2}' $(MAKEFILE_LIST)
	@echo; echo "  Vars: ENV=$(ENV) IP=<vps-ip> TAGS=<ansible tags> SSH_USER=$(SSH_USER)"

bootstrap: ## New VPS: install your SSH key (asks for the provider's password once). IP=x.x.x.x
	@test -n "$(IP)" || { echo "usage: make bootstrap IP=<vps-ip>"; exit 1; }
	ssh-copy-id -o StrictHostKeyChecking=accept-new $(SSH_USER)@$(IP)
	ssh $(SSH_USER)@$(IP) 'sudo -n true && echo "sudo OK"'

init: ## terraform init
	terraform -chdir=$(TF_DIR) init

plan: ## terraform plan (DNS + provisioning)
	terraform -chdir=$(TF_DIR) plan

apply: ## terraform apply: DNS records, then the full Ansible playbook
	terraform -chdir=$(TF_DIR) apply

dns: ## Only DNS (skip Ansible)
	terraform -chdir=$(TF_DIR) apply -var run_ansible=false

provision: ## Run Ansible directly (uses the Terraform-generated inventory). TAGS=nginx,jenkins
	cd $(ANSIBLE_DIR) && ansible-galaxy collection install -r requirements.yml >/dev/null && \
	  ansible-playbook -i $(INVENTORY) playbooks/site.yml $(VAULT_ARG) $(TAGS_ARG)

check: ## Ansible dry run with diff against the server (changes nothing)
	cd $(ANSIBLE_DIR) && ansible-playbook -i $(INVENTORY) playbooks/site.yml $(VAULT_ARG) $(TAGS_ARG) --check --diff

lint: ## terraform fmt/validate + yamllint + ansible-lint
	terraform fmt -recursive -check infra/terraform
	terraform -chdir=$(TF_DIR) init -backend=false -input=false >/dev/null && terraform -chdir=$(TF_DIR) validate
	cd $(ANSIBLE_DIR) && yamllint . && ansible-lint

fmt: ## terraform fmt
	terraform fmt -recursive infra/terraform

vault-edit: ## Edit encrypted Ansible secrets
	cd $(ANSIBLE_DIR) && ansible-vault edit inventories/$(ENV)/group_vars/all/secrets.yml $(VAULT_ARG)

vault-view: ## Show encrypted Ansible secrets
	cd $(ANSIBLE_DIR) && ansible-vault view inventories/$(ENV)/group_vars/all/secrets.yml $(VAULT_ARG)
