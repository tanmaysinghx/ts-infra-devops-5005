output "zone_id" {
  description = "Cloudflare zone id."
  value       = data.cloudflare_zone.this.zone_id
}

output "fqdns" {
  description = "Fully-qualified names of the managed records."
  value       = sort([for r in cloudflare_dns_record.a : r.name])
}
