package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorSpecialtyDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.SubSpecialtyDetailsDto;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorGetService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;


    @Cacheable(value = "doctor_cache", key = "#id")
    @Transactional(readOnly = true)
    public DoctorResponseDto getDoctorById(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        List<DoctorSpecialtyDto> groupedSpecialties = groupSubSpecialtiesBySpecialty(doctor.getSubSpecialties());

        return doctorMapper.toDoctorDetailsWithGroupedSpecialties(doctor, groupedSpecialties);
    }

    @Transactional(readOnly = true)
    public List<DoctorResponseDto> getAllDoctors() {
        return doctorRepository.findAll()
                .stream()
                .map(doctor -> {
                    List<DoctorSpecialtyDto> groupedSpecialties = groupSubSpecialtiesBySpecialty(doctor.getSubSpecialties());
                    return doctorMapper.toDoctorDetailsWithGroupedSpecialties(doctor, groupedSpecialties);
                })
                .toList();
    }

    private List<DoctorSpecialtyDto> groupSubSpecialtiesBySpecialty(List<SubSpecialty> subSpecialties) {
        // Agrupar subespecialidades por su especialidad
        Map<Speciality, List<SubSpecialty>> grouped = subSpecialties.stream()
                .collect(Collectors.groupingBy(SubSpecialty::getSpeciality));

        List<DoctorSpecialtyDto> doctorSpecialtyDtos = new ArrayList<>();
        grouped.forEach((speciality, subs) -> {
            // Mapear cada SubSpecialty a SubSpecialtyDetailsDto
            List<SubSpecialtyDetailsDto> subDetails = subs.stream()
                    .map(sub -> new SubSpecialtyDetailsDto(sub.getName(), sub.getCodeSubSpecialty()))
                    .collect(Collectors.toList());

            // Crear el DoctorSpecialtyDto para esta especialidad y sus subespecialidades
            doctorSpecialtyDtos.add(
                    new DoctorSpecialtyDto(
                            speciality.getName(),
                            speciality.getCodeSpeciality(),
                            subDetails
                    )
            );
        });
        return doctorSpecialtyDtos;
    }
}
