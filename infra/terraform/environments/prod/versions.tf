terraform {
  required_version = ">= 1.6"

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }

  # State is local (git-ignored) by default. For a shared/remote state, use an
  # S3-compatible bucket, e.g. Cloudflare R2:
  # backend "s3" {
  #   bucket                      = "ts-infra-tfstate"
  #   key                         = "prod/terraform.tfstate"
  #   region                      = "auto"
  #   endpoints                   = { s3 = "https://<account_id>.r2.cloudflarestorage.com" }
  #   skip_credentials_validation = true
  #   skip_region_validation      = true
  #   skip_requesting_account_id  = true
  #   skip_metadata_api_check     = true
  #   use_path_style              = true
  # }
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
