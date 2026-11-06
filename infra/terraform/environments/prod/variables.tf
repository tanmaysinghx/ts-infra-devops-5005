variable "environment" {
  description = "Ansible inventory to use (infra/ansible/inventories/<environment>)."
  type        = string
  default     = "prod"
}

variable "server_ip" {
  description = "Public IPv4 of the VPS (from the provider's control panel / welcome email)."
  type        = string

  validation {
    condition     = can(cidrhost("${var.server_ip}/32", 0))
    error_message = "server_ip must be an IPv4 address."
  }
}

variable "server_name" {
  description = "Inventory name for the host."
  type        = string
  default     = "prod-vps-01"
}

variable "ssh_user" {
  description = "Sudo-capable SSH user on the VPS."
  type        = string
  default     = "ubuntu"
}

variable "zone_name" {
  description = "Cloudflare zone."
  type        = string
  default     = "tanmaysinghx.com"
}

variable "cloudflare_api_token" {
  description = "Cloudflare API token with Zone:DNS:Edit on the zone. Prefer TF_VAR_cloudflare_api_token."
  type        = string
  sensitive   = true
  default     = null
}

variable "manage_dns" {
  description = "Create/update the A records in Cloudflare."
  type        = bool
  default     = true
}

variable "extra_dns_records" {
  description = "Additional A records not derived from nginx_sites (key = subdomain)."
  type = map(object({
    ip      = string
    proxied = optional(bool, false)
  }))
  default = {}
}

variable "run_ansible" {
  description = "Run the Ansible playbook against the VPS."
  type        = bool
  default     = true
}

variable "ansible_vault_password_file" {
  description = "Path to the ansible-vault password file for secrets.yml."
  type        = string
  default     = "~/.ansible/ts-infra-vault-pass"
}

variable "ansible_extra_args" {
  description = "Extra args for ansible-playbook (e.g. \"--tags nginx\" or \"--check --diff\")."
  type        = string
  default     = ""
}
