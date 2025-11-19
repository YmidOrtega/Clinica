package com.ClinicaDeYmid.suppliers_service.module.service.validation;

import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorValidationService {

    private final DoctorRepository doctorRepository;

    // Patrones de validación
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9]{7,20}$"
    );

    private static final Pattern LICENSE_PATTERN = Pattern.compile(
            "^[A-Z]{2,4}-[0-9]{4,10}$"
    );

    @Transactional(readOnly = true)
    public void validateUniqueFields(
            String email,
            String licenseNumber,
            Integer providerCode,
            String identificationNumber,
            Long doctorId) {

        log.debug("Validating unique fields for doctor (ID: {})", doctorId);

        List<String> errors = new ArrayList<>();

        // Validar email único
        if (email != null && doctorRepository.existsByEmail(email, doctorId)) {
            errors.add(String.format("El email '%s' ya está registrado", email));
        }

        // Validar licencia única
        if (licenseNumber != null && doctorRepository.existsByLicenseNumber(licenseNumber, doctorId)) {
            errors.add(String.format("La licencia médica '%s' ya está registrada", licenseNumber));
        }

        // Validar código de proveedor único
        if (providerCode != null && doctorRepository.existsByProviderCode(providerCode, doctorId)) {
            errors.add(String.format("El código de proveedor '%d' ya está registrado", providerCode));
        }

        // Validar número de identificación único
        if (identificationNumber != null &&
                doctorRepository.existsByIdentificationNumber(identificationNumber, doctorId)) {
            errors.add(String.format("El número de identificación '%s' ya está registrado",
                    identificationNumber));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Errores de validación de unicidad", errors);
        }
    }

    /**
     * Valida el formato del email
     */
    public void validateEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("El email es obligatorio");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException(
                    String.format("El email '%s' no tiene un formato válido", email));
        }
    }

    /**
     * Valida el formato del teléfono
     */
    public void validatePhoneFormat(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ValidationException("El teléfono es obligatorio");
        }

        // Limpiar espacios y guiones para la validación
        String cleanPhone = phoneNumber.replaceAll("[\\s-]", "");

        if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
            throw new ValidationException(
                    String.format("El teléfono '%s' no tiene un formato válido. " +
                                    "Debe contener entre 7 y 20 dígitos, puede incluir '+' al inicio",
                            phoneNumber));
        }
    }

    /**
     * Valida el formato de la licencia médica
     */
    public void validateLicenseFormat(String licenseNumber) {
        if (licenseNumber == null || licenseNumber.trim().isEmpty()) {
            throw new ValidationException("La licencia médica es obligatoria");
        }

        if (!LICENSE_PATTERN.matcher(licenseNumber).matches()) {
            throw new ValidationException(
                    String.format("La licencia médica '%s' no tiene un formato válido. " +
                            "Formato esperado: AA-1234 o AAA-12345 (2-4 letras mayúsculas, " +
                            "guion, 4-10 números)", licenseNumber));
        }
    }

    /**
     * Valida el código de proveedor
     */
    public void validateProviderCode(Integer providerCode) {
        if (providerCode == null) {
            throw new ValidationException("El código de proveedor es obligatorio");
        }

        if (providerCode <= 0) {
            throw new ValidationException(
                    "El código de proveedor debe ser un número positivo");
        }

        if (providerCode > 999999) {
            throw new ValidationException(
                    "El código de proveedor no puede exceder 999999");
        }
    }

    /**
     * Valida el número de identificación
     */
    public void validateIdentificationNumber(String identificationNumber) {
        if (identificationNumber == null || identificationNumber.trim().isEmpty()) {
            throw new ValidationException("El número de identificación es obligatorio");
        }

        if (identificationNumber.length() < 5 || identificationNumber.length() > 20) {
            throw new ValidationException(
                    "El número de identificación debe tener entre 5 y 20 caracteres");
        }

        // Validar que contenga solo números, letras y guiones
        if (!identificationNumber.matches("^[A-Za-z0-9-]+$")) {
            throw new ValidationException(
                    "El número de identificación solo puede contener letras, números y guiones");
        }
    }

    /**
     * Valida el nombre y apellido
     */
    public void validateName(String name, String lastName) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("El nombre es obligatorio");
        }

        if (lastName == null || lastName.trim().isEmpty()) {
            throw new ValidationException("El apellido es obligatorio");
        }

        if (name.length() < 2 || name.length() > 100) {
            throw new ValidationException(
                    "El nombre debe tener entre 2 y 100 caracteres");
        }

        if (lastName.length() < 2 || lastName.length() > 100) {
            throw new ValidationException(
                    "El apellido debe tener entre 2 y 100 caracteres");
        }

        // Validar que solo contengan letras, espacios, tildes y apóstrofes
        String namePattern = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ' ]+$";

        if (!name.matches(namePattern)) {
            throw new ValidationException(
                    "El nombre solo puede contener letras, espacios y tildes");
        }

        if (!lastName.matches(namePattern)) {
            throw new ValidationException(
                    "El apellido solo puede contener letras, espacios y tildes");
        }
    }

    /**
     * Valida que las especialidades no estén vacías
     */
    public void validateSpecialties(List<Long> specialtyIds) {
        if (specialtyIds == null || specialtyIds.isEmpty()) {
            throw new ValidationException(
                    "El doctor debe tener al menos una especialidad");
        }

        // Validar que no haya IDs duplicados
        long distinctCount = specialtyIds.stream().distinct().count();
        if (distinctCount != specialtyIds.size()) {
            throw new ValidationException(
                    "La lista de especialidades contiene IDs duplicados");
        }

        // Validar que todos los IDs sean positivos
        boolean hasInvalidIds = specialtyIds.stream().anyMatch(id -> id == null || id <= 0);
        if (hasInvalidIds) {
            throw new ValidationException(
                    "Todos los IDs de especialidades deben ser números positivos");
        }
    }

    /**
     * Valida subespecialidades (opcional, pero sin duplicados si se proveen)
     */
    public void validateSubSpecialties(List<Long> subSpecialtyIds) {
        if (subSpecialtyIds == null || subSpecialtyIds.isEmpty()) {
            return; // Las subespecialidades son opcionales
        }

        // Validar que no haya IDs duplicados
        long distinctCount = subSpecialtyIds.stream().distinct().count();
        if (distinctCount != subSpecialtyIds.size()) {
            throw new ValidationException(
                    "La lista de subespecialidades contiene IDs duplicados");
        }

        // Validar que todos los IDs sean positivos
        boolean hasInvalidIds = subSpecialtyIds.stream().anyMatch(id -> id == null || id <= 0);
        if (hasInvalidIds) {
            throw new ValidationException(
                    "Todos los IDs de subespecialidades deben ser números positivos");
        }
    }

    /**
     * Valida la tarifa por hora
     */
    public void validateHourlyRate(Double hourlyRate) {
        if (hourlyRate == null) {
            return; // La tarifa es opcional
        }

        if (hourlyRate < 0) {
            throw new ValidationException(
                    "La tarifa por hora no puede ser negativa");
        }

        if (hourlyRate > 1000000) {
            throw new ValidationException(
                    "La tarifa por hora no puede exceder 1,000,000");
        }
    }

    /**
     * Validación completa para creación de doctor
     */
    @Transactional(readOnly = true)
    public void validateDoctorCreation(
            String name,
            String lastName,
            String email,
            String phoneNumber,
            String licenseNumber,
            Integer providerCode,
            String identificationNumber,
            List<Long> specialtyIds,
            List<Long> subSpecialtyIds,
            Double hourlyRate) {

        log.info("Validating doctor creation");

        // Validaciones de formato
        validateName(name, lastName);
        validateEmailFormat(email);
        validatePhoneFormat(phoneNumber);
        validateLicenseFormat(licenseNumber);
        validateProviderCode(providerCode);
        validateIdentificationNumber(identificationNumber);
        validateSpecialties(specialtyIds);
        validateSubSpecialties(subSpecialtyIds);
        validateHourlyRate(hourlyRate);

        // Validaciones de unicidad
        validateUniqueFields(email, licenseNumber, providerCode, identificationNumber, null);

        log.info("Doctor creation validation passed successfully");
    }

    /**
     * Validación completa para actualización de doctor
     */
    @Transactional(readOnly = true)
    public void validateDoctorUpdate(
            Long doctorId,
            String name,
            String lastName,
            String email,
            String phoneNumber,
            String licenseNumber,
            Integer providerCode,
            String identificationNumber,
            List<Long> specialtyIds,
            List<Long> subSpecialtyIds,
            Double hourlyRate) {

        log.info("Validating doctor update for ID: {}", doctorId);

        // Validaciones de formato (solo campos no nulos)
        if (name != null && lastName != null) {
            validateName(name, lastName);
        }
        if (email != null) {
            validateEmailFormat(email);
        }
        if (phoneNumber != null) {
            validatePhoneFormat(phoneNumber);
        }
        if (licenseNumber != null) {
            validateLicenseFormat(licenseNumber);
        }
        if (providerCode != null) {
            validateProviderCode(providerCode);
        }
        if (identificationNumber != null) {
            validateIdentificationNumber(identificationNumber);
        }
        if (specialtyIds != null) {
            validateSpecialties(specialtyIds);
        }
        if (subSpecialtyIds != null) {
            validateSubSpecialties(subSpecialtyIds);
        }
        if (hourlyRate != null) {
            validateHourlyRate(hourlyRate);
        }

        // Validaciones de unicidad
        validateUniqueFields(email, licenseNumber, providerCode, identificationNumber, doctorId);

        log.info("Doctor update validation passed successfully");
    }
}