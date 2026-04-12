{{/*
Expand the name of the chart.
*/}}
{{- define "omcsi.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "omcsi.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "omcsi.labels" -}}
helm.sh/chart: {{ include "omcsi.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Selector labels for a component.
Usage: include "omcsi.selectorLabels" (dict "root" . "component" "minecraft-wrapper")
*/}}
{{- define "omcsi.selectorLabels" -}}
app.kubernetes.io/name: {{ include "omcsi.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Pod affinity rule that co-locates pods mounting the mcserver PVC on the
same node. Required because the PVC defaults to ReadWriteOnce.
Usage: include "omcsi.mcserverAffinity" .
*/}}
{{- define "omcsi.mcserverAffinity" -}}
affinity:
  podAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app.kubernetes.io/name: {{ include "omcsi.name" . }}
              app.kubernetes.io/instance: {{ .Release.Name }}
              app.kubernetes.io/component: minecraft-wrapper
          topologyKey: kubernetes.io/hostname
{{- end }}

{{/*
Full service DNS name helpers used in environment variables so that
services can discover each other inside the cluster.
*/}}
{{- define "omcsi.minecraftWrapperHost" -}}
{{ include "omcsi.fullname" . }}-minecraft-wrapper
{{- end }}

{{- define "omcsi.webappHost" -}}
{{ include "omcsi.fullname" . }}-webapp
{{- end }}

{{- define "omcsi.alertManagerHost" -}}
{{ include "omcsi.fullname" . }}-alert-manager
{{- end }}

{{- define "omcsi.backupManagerHost" -}}
{{ include "omcsi.fullname" . }}-backup-manager
{{- end }}

{{- define "omcsi.agentManagerHost" -}}
{{ include "omcsi.fullname" . }}-agent-manager
{{- end }}

{{- define "omcsi.secretName" -}}
{{ include "omcsi.fullname" . }}-secrets
{{- end }}
