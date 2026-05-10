# =============================================================================
# Outputs
# =============================================================================

output "helm_release_name" {
  description = "Name of the OMCSI Helm release."
  value       = module.omcsi.helm_release_name
}

output "helm_release_namespace" {
  description = "Namespace of the OMCSI Helm release."
  value       = module.omcsi.helm_namespace
}

output "helm_release_status" {
  description = "Status of the OMCSI Helm release."
  value       = module.omcsi.status
}
