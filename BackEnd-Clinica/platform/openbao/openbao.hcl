ui            = false
disable_mlock = true
cluster_name  = "clinica"

storage "raft" {
  path = "/openbao/file"

  retry_join {
    leader_api_addr     = "https://openbao-1:8200"
    leader_ca_cert_file = "/openbao/tls/ca.crt"
  }
  retry_join {
    leader_api_addr     = "https://openbao-2:8200"
    leader_ca_cert_file = "/openbao/tls/ca.crt"
  }
  retry_join {
    leader_api_addr     = "https://openbao-3:8200"
    leader_ca_cert_file = "/openbao/tls/ca.crt"
  }
}

listener "tcp" {
  address         = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  tls_cert_file   = "/openbao/tls/server.crt"
  tls_key_file    = "/openbao/tls/server.key"
  tls_min_version = "tls13"
}

audit "file" "file" {
  options {
    file_path = "/openbao/logs/audit.log"
  }
}

seal "static" {
  current_key_id = "dev-1"
  current_key    = "file:///openbao/seal/unseal.key"
}
