variable "zone_name" {
  description = "Cloudflare zone (apex domain), e.g. tanmaysinghx.com."
  type        = string
}

variable "records" {
  description = "A records keyed by subdomain (\"@\" for the apex)."
  type = map(object({
    ip      = string
    proxied = optional(bool, false)
  }))
}

variable "ttl" {
  description = "TTL in seconds for non-proxied records."
  type        = number
  default     = 300
}
