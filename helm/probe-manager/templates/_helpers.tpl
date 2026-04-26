{{- define "probe-manager.fullname" -}}
{{- .Release.Name }}
{{- end }}

{{- define "probe-manager.labels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
