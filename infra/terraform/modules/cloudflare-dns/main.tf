data "cloudflare_zone" "this" {
  filter = {
    name = var.zone_name
  }
}

resource "cloudflare_dns_record" "a" {
  for_each = var.records

  zone_id = data.cloudflare_zone.this.zone_id
  name    = each.key == "@" ? var.zone_name : "${each.key}.${var.zone_name}"
  type    = "A"
  content = each.value.ip
  proxied = each.value.proxied
  ttl     = each.value.proxied ? 1 : var.ttl # 1 = "automatic", required when proxied
  comment = "Managed by Terraform (ts-infra-devops-5005)"
}
