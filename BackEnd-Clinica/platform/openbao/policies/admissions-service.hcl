path "secret/data/admissions/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/admissions/db/app" {
  capabilities = ["read"]
}

path "transit/keys/admissions-seal" {
  capabilities = ["read"]
}

path "transit/sign/admissions-seal" {
  capabilities = ["update"]
}

path "transit/keys/admissions-service-client" {
  capabilities = ["read"]
}

path "transit/sign/admissions-service-client" {
  capabilities = ["update"]
}
