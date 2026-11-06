output "inventory_path" {
  description = "Generated Ansible inventory."
  value       = local_file.inventory.filename
}

output "ansible_hash" {
  description = "Hash of the Ansible tree that was (or will be) applied."
  value       = local.ansible_hash
}
