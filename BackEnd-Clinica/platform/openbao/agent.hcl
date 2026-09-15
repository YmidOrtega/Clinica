pid_file = "/tmp/openbao-agent.pid"

vault {
  address = "https://openbao:8200"
  ca_cert = "/openbao/tls/ca.crt"
  retry {
    num_retries = -1
  }
}

auto_auth {
  method "approle" {
    config = {
      role_id_file_path                   = "/openbao/approle/role-id"
      secret_id_file_path                 = "/openbao/approle/secret-id"
      remove_secret_id_file_after_reading = false
    }
  }
}

template_config {
  exit_on_retry_failure = false
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/root\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/patient-db/root-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/migrator\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/patient-db/migrator-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/migrator\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/patient-db/migrator-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/app\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/patient-db/app-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/app\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/patient-db/app-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/debezium\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/patient-db/debezium-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/debezium\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/patient-db/debezium-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/root\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/clinical-db/root-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/migrator\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/clinical-db/migrator-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/migrator\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/clinical-db/migrator-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/app\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/clinical-db/app-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/app\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/clinical-db/app-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/debezium\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/clinical-db/debezium-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/debezium\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/clinical-db/debezium-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/storage/root\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/clinical-storage/root-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/storage/root\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/clinical-storage/root-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/storage/attachments\" }}{{ index .Data.data \"access-key\" }}{{ end }}"
  destination = "/rendered/clinical-storage/attachments-access-key"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/storage/attachments\" }}{{ index .Data.data \"secret-key\" }}{{ end }}"
  destination = "/rendered/clinical-storage/attachments-secret-key"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/debezium\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/kafka-connect/patient-db-debezium-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/patient/db/debezium\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/kafka-connect/patient-db-debezium-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/debezium\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/kafka-connect/clinical-db-debezium-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/clinical/db/debezium\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/kafka-connect/clinical-db-debezium-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/auth/db/root\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/auth-db/root-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/auth/db/migrator\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/auth-db/migrator-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/auth/db/migrator\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/auth-db/migrator-password"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/auth/db/app\" }}{{ .Data.data.username }}{{ end }}"
  destination = "/rendered/auth-db/app-user"
  perms       = "0444"
}

template {
  contents    = "{{ with secret \"secret/data/auth/db/app\" }}{{ .Data.data.password }}{{ end }}"
  destination = "/rendered/auth-db/app-password"
  perms       = "0444"
}
