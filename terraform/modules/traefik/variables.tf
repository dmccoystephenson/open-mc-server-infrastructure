variable "enable_traefik" {
  description = "Deploy Traefik as the single-entrypoint ingress controller, routing Minecraft TCP (25565), HTTP (80), and HTTPS (443) through one LoadBalancer IP. When enabled, set minecraft_service_type and nginx_service_type to 'ClusterIP'."
  type        = bool
  default     = false
}

variable "helm_namespace" {
  description = "Kubernetes namespace where OMCSI services are deployed. Used to build cluster-internal DNS addresses for Traefik routing."
  type        = string
  default     = "omcsi"
}

variable "helm_release_name" {
  description = "OMCSI Helm release name. Used to build cluster-internal service DNS addresses for Traefik routing."
  type        = string
  default     = "omcsi"
}

variable "enable_grafana_route" {
  description = "Expose a Traefik TCP entrypoint on port 3000 forwarding to Grafana. Only enable when Grafana is installed in the cluster (e.g. via kube-prometheus-stack)."
  type        = bool
  default     = false
}

variable "grafana_service_address" {
  description = "Cluster-internal DNS address (host:port) of the Grafana Service. Only used when enable_grafana_route is true."
  type        = string
  default     = "kube-prometheus-stack-grafana.monitoring.svc.cluster.local:80"
}
