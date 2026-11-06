locals {
  # Re-run Ansible whenever anything in the Ansible tree changes (roles, vars,
  # encrypted secrets). Generated inventories are excluded to avoid a loop.
  ansible_files = sort([
    for f in fileset(var.ansible_dir, "**") : f
    if !can(regex("^inventories/[^/]+/hosts\\.yml$", f)) && !strcontains(f, "/.") && !startswith(f, ".")
  ])
  ansible_hash = sha1(join("", [for f in local.ansible_files : filesha1("${var.ansible_dir}/${f}")]))

  vault_arg = var.vault_password_file == "" ? "" : "--vault-password-file ${pathexpand(var.vault_password_file)}"
}

resource "local_file" "inventory" {
  filename        = var.inventory_path
  file_permission = "0644"
  content = yamlencode({
    all = {
      children = {
        vps = {
          hosts = {
            (var.host_name) = {
              ansible_host            = var.host
              ansible_user            = var.ssh_user
              ansible_become          = true
              ansible_ssh_common_args = "-o StrictHostKeyChecking=accept-new"
            }
          }
        }
      }
    }
  })
}

resource "terraform_data" "ansible" {
  count = var.enabled ? 1 : 0

  triggers_replace = {
    host         = var.host
    ansible_hash = local.ansible_hash
    inventory    = local_file.inventory.content
  }

  provisioner "local-exec" {
    working_dir = var.ansible_dir
    command     = <<-EOT
      set -eu
      ansible-galaxy collection install -r requirements.yml >/dev/null
      ansible-playbook -i '${abspath(var.inventory_path)}' playbooks/site.yml ${local.vault_arg} ${var.extra_args}
    EOT
    interpreter = ["/bin/bash", "-c"]
  }
}
