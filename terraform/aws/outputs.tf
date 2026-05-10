# =============================================================================
# Outputs
# =============================================================================

output "cluster_name" {
  description = "EKS cluster name."
  value       = aws_eks_cluster.omcsi.name
}

output "cluster_endpoint" {
  description = "EKS cluster API endpoint."
  value       = aws_eks_cluster.omcsi.endpoint
}

output "cluster_version" {
  description = "EKS cluster Kubernetes version."
  value       = aws_eks_cluster.omcsi.version
}

output "cluster_arn" {
  description = "ARN of the EKS cluster."
  value       = aws_eks_cluster.omcsi.arn
}

output "kubeconfig_command" {
  description = "Command to update your local kubeconfig for kubectl access."
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.omcsi.name}"
}

output "helm_release_status" {
  description = "Status of the OMCSI Helm release."
  value       = module.omcsi.status
}
