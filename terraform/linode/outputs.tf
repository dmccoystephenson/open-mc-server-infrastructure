# =============================================================================
# Outputs
# =============================================================================

output "cluster_id" {
  description = "LKE cluster ID."
  value       = linode_lke_cluster.omcsi.id
}

output "cluster_label" {
  description = "LKE cluster label."
  value       = linode_lke_cluster.omcsi.label
}

output "cluster_status" {
  description = "LKE cluster status."
  value       = linode_lke_cluster.omcsi.status
}

output "cluster_api_endpoints" {
  description = "Kubernetes API endpoints for the LKE cluster."
  value       = linode_lke_cluster.omcsi.api_endpoints
}

output "kubeconfig_path" {
  description = "Path to the generated kubeconfig file."
  value       = local_sensitive_file.kubeconfig.filename
}

output "helm_release_status" {
  description = "Status of the OMCSI Helm release."
  value       = helm_release.omcsi.status
}

output "get_nginx_ip_command" {
  description = "Run after apply to retrieve the nginx LoadBalancer endpoint as tab-separated columns: service name, ingress IP, ingress hostname."
  value       = "kubectl get svc -n ${var.helm_namespace} -l app.kubernetes.io/component=nginx --kubeconfig ${local_sensitive_file.kubeconfig.filename} -o jsonpath='{range .items[*]}{.metadata.name}{\"\\t\"}{.status.loadBalancer.ingress[0].ip}{\"\\t\"}{.status.loadBalancer.ingress[0].hostname}{\"\\n\"}{end}'"
}

output "get_traefik_ip_command" {
  description = "Run after apply to retrieve the Traefik LoadBalancer IP (only populated when enable_traefik = true)."
  value       = var.enable_traefik ? "kubectl get svc -n traefik traefik --kubeconfig ${local_sensitive_file.kubeconfig.filename} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'" : null
}
