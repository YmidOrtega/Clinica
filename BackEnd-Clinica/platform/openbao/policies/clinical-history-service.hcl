path "secret/data/clinical/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/clinical/db/app" {
  capabilities = ["read"]
}

path "secret/data/clinical/storage/attachments" {
  capabilities = ["read"]
}

path "secret/data/clinical/retired-master-keys" {
  capabilities = ["read"]
}

path "secret/data/clinical/retired-seal-keys" {
  capabilities = ["read"]
}

path "transit/keys/clinical-kek" {
  capabilities = ["read"]
}

path "transit/encrypt/clinical-kek" {
  capabilities = ["update"]
}

path "transit/decrypt/clinical-kek" {
  capabilities = ["update"]
}

path "transit/keys/clinical-seal" {
  capabilities = ["read"]
}

path "transit/sign/clinical-seal" {
  capabilities = ["update"]
}

path "transit/keys/clinical-history-service-client" {
  capabilities = ["read"]
}

path "transit/sign/clinical-history-service-client" {
  capabilities = ["update"]
}
