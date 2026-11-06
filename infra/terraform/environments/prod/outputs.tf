output "server_ip" {
  description = "VPS public IP."
  value       = var.server_ip
}

output "dns_records" {
  description = "Hostnames pointing at the VPS."
  value       = var.manage_dns ? module.dns[0].fqdns : []
}

output "urls" {
  description = "Public HTTPS endpoints."
  value       = [for f in local.site_fqdns : "https://${f}"]
}

output "ansible_inventory" {
  description = "Generated inventory; reuse with: ansible-playbook -i <this> playbooks/site.yml"
  value       = module.provision.inventory_path
}
