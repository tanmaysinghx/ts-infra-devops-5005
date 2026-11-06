variable "enabled" {
  description = "Run the Ansible playbook. Set false to only manage DNS."
  type        = bool
  default     = true
}

variable "host" {
  description = "Public IP (or DNS name) of the VPS."
  type        = string
}

variable "host_name" {
  description = "Inventory host name."
  type        = string
  default     = "prod-vps-01"
}

variable "ssh_user" {
  description = "Sudo-capable SSH user (key auth must already work)."
  type        = string
  default     = "ubuntu"
}

variable "ansible_dir" {
  description = "Path to infra/ansible."
  type        = string
}

variable "inventory_path" {
  description = "Where to write the generated inventory (git-ignored)."
  type        = string
}

variable "vault_password_file" {
  description = "ansible-vault password file for secrets.yml (empty = none)."
  type        = string
  default     = ""
}

variable "extra_args" {
  description = "Extra ansible-playbook arguments, e.g. \"--tags nginx\"."
  type        = string
  default     = ""
}
