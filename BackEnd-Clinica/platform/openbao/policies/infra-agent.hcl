path "secret/data/patient/db/*" {
  capabilities = ["read"]
}

path "secret/data/clinical/db/*" {
  capabilities = ["read"]
}

path "secret/data/contracting/db/*" {
  capabilities = ["read"]
}

path "secret/data/practitioners/db/*" {
  capabilities = ["read"]
}

path "secret/data/admissions/db/*" {
  capabilities = ["read"]
}

path "secret/data/auth/db/*" {
  capabilities = ["read"]
}

path "secret/data/clinical/storage/*" {
  capabilities = ["read"]
}

path "secret/data/gateway/redis" {
  capabilities = ["read"]
}
