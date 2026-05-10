# =============================================================================
# Traefik ingress controller (optional — enabled via enable_traefik = true)
# =============================================================================

module "traefik" {
  source = "../modules/traefik"

  enable_traefik          = var.enable_traefik
  helm_namespace          = var.helm_namespace
  helm_release_name       = var.helm_release_name
  enable_grafana_route    = var.enable_grafana_route
  grafana_service_address = var.grafana_service_address

  depends_on = [module.omcsi]
}
