path "secret/data/patient/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/patient/db/app" {
  capabilities = ["read"]
}

path "transit/keys/patient-service-client" {
  capabilities = ["read"]
}

path "transit/sign/patient-service-client" {
  capabilities = ["update"]
}
