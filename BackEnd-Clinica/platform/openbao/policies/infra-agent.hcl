path "secret/data/patient/db/*" {
  capabilities = ["read"]
}

path "secret/data/clinical/db/*" {
  capabilities = ["read"]
}

path "secret/data/clinical/storage/*" {
  capabilities = ["read"]
}
