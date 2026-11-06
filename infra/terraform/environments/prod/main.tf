locals {
  ansible_dir = abspath("${path.module}/../../../ansible")

  # The Ansible vars are the single source of truth for which sites exist:
  # every enabled nginx site gets an A record pointing at the VPS.
  ansible_vars = yamldecode(file("${local.ansible_dir}/inventories/${var.environment}/group_vars/all/main.yml"))
  site_fqdns = [
    for s in local.ansible_vars.nginx_sites : s.server_name
    if try(s.enabled, true)
  ]
  site_records = {
    for fqdn in local.site_fqdns :
    trimsuffix(fqdn, ".${var.zone_name}") => { ip = var.server_ip, proxied = false }
    if endswith(fqdn, ".${var.zone_name}")
  }
  records = merge(local.site_records, var.extra_dns_records)
}

module "dns" {
  source = "../../modules/cloudflare-dns"
  count  = var.manage_dns ? 1 : 0

  zone_name = var.zone_name
  records   = local.records
}

module "provision" {
  source = "../../modules/ansible-provision"
  # DNS first, so Let's Encrypt can validate during the playbook.
  depends_on = [module.dns]

  enabled             = var.run_ansible
  host                = var.server_ip
  host_name           = var.server_name
  ssh_user            = var.ssh_user
  ansible_dir         = local.ansible_dir
  inventory_path      = "${local.ansible_dir}/inventories/${var.environment}/hosts.yml"
  vault_password_file = var.ansible_vault_password_file
  extra_args          = var.ansible_extra_args
}
