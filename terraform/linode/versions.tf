terraform {
  required_version = ">= 1.3.0"

  required_providers {
    linode = {
      source  = "linode/linode"
      version = "~> 2.36"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.36"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}
