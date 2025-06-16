package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorRecordService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    public DoctorResponseDTO createDoctor(DoctorCreateRequestDTO request) {

        Doctor doctor = doctorMapper.toEntity(request);

        doctorRepository.save(doctor);

        return doctorMapper.toResponse(doctor);
    }

    public DoctorResponseDTO updateDoctor(Long id, DoctorUpdateRequestDTO request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        doctorMapper.updateDoctorFromDto(request, doctor);

        doctorRepository.save(doctor);

        return doctorMapper.toResponse(doctor);
    }

    /*
    public DoctorResponse getDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));


        List<SubSpecialtyDTO> subSpecialties = fetchSubSpecialties(doctor.getSubSpecialties());
        List<ServiceTypeDTO> services = fetchServiceTypes(doctor.getAllowedServiceTypeIds());
        List<AttentionDTO> attentions = fetchAttentions(doctor.getId());

        return doctorMapper.toDto(doctor, subSpecialties, services, attentions);
    }*/
}

