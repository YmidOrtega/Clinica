#!/bin/bash

# Cargar variables de entorno
export PATIENT_DB_ROOT_PASSWORD='SecureRootPass2024!'
export PATIENT_DB_NAME='patient_db'
export PATIENT_DB_USER='patient_user'
export PATIENT_DB_PASSWORD='PatientSecure123!'
export PATIENT_DB_HOST='jdbc:mysql://localhost:3307/patient_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'

export BILLING_DB_ROOT_PASSWORD='BillingRootPass2024!'
export BILLING_DB_NAME='billing_db'
export BILLING_DB_USER='billing_user'
export BILLING_DB_PASSWORD='BillingSecure123!'
export BILLING_DB_HOST='jdbc:mysql://localhost:3308/billing_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'

export ADMISSIONS_DB_ROOT_PASSWORD='AdmissionsRootPass2024!'
export ADMISSIONS_DB_NAME='admissions_db'
export ADMISSIONS_DB_USER='admissions_user'
export ADMISSIONS_DB_PASSWORD='AdmissionsSecure123!'
export ADMISSIONS_DB_HOST='localhost:3309'

export AI_ASSISTANT_DB_ROOT_PASSWORD='AIRootPass2024!'
export AI_ASSISTANT_DB_NAME='ai_assistant_db'
export AI_ASSISTANT_DB_USER='ai_user'
export AI_ASSISTANT_DB_PASSWORD='AISecure123!'
export AI_ASSISTANT_DB_HOST='localhost:3310'

export SUPPLIERS_DB_ROOT_PASSWORD='SuppliersRootPass2024!'
export SUPPLIERS_DB_NAME='suppliers_db'
export SUPPLIERS_DB_USER='suppliers_user'
export SUPPLIERS_DB_PASSWORD='SuppliersSecure123!'
export SUPPLIERS_DB_HOST='jdbc:mysql://localhost:3311/suppliers_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'

export AUTH_DB_ROOT_PASSWORD='AuthRootPass2024!'
export AUTH_DB_NAME='auth_db'
export AUTH_DB_USER='auth_user'
export AUTH_DB_PASSWORD='AuthSecure123!'
export AUTH_DB_HOST='jdbc:mysql://localhost:3312/auth_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'

export CLIENTS_DB_ROOT_PASSWORD='ClientsRootPass2024!'
export CLIENTS_DB_NAME='clients_db'
export CLIENTS_DB_USER='clients_user'
export CLIENTS_DB_PASSWORD='ClientsSecure123!'
export CLIENTS_DB_HOST='jdbc:mysql://localhost:3313/clients_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'

export JWT_SECRET='MiClaveSecretaSuperSeguraParaJWT2024!@#$%^&*()'
export JWT_EXPIRATION='3600'
export JWT_PUBLIC_KEY='MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwdfg9sVVb7eV5uS2S0xDjplfIl3UBq3TkvC8agricVEw6nqy6SCA6xAnK+wYW7+iDeWQG3cn97J57Dz+eOZQ8jp2xwrEnEyCVq1Nv/5uuL5x5EXaR8VjVOxUvdkChKgVA7Zsn+WlGSK2LxFIpvXWS4utzbrmbAd2SBfjXNP/LSqfey45wqu0H10wh/D/k+IdCeOWDt4VA/XuUGEkuZkcJ6GvSj99EfQPo5Ji6nWL/pg3geDaFUzwDk5nrDdkAqBjzHPqB6WsL3rFe3S8oM2T3ecN5VOH4cwvtTOhXApNaB4+9Gl0As/5eUoi4zZeSDs2wupbmUPgigTpTHljcEVZ3wIDAQAB'

export REDIS_PASSWORD='tu_password_redis_seguro'

export AI_ASSISTANT_GEMINI_PROJECT_ID='gen-lang-client-0771416717'

echo "Variables de entorno cargadas correctamente"
