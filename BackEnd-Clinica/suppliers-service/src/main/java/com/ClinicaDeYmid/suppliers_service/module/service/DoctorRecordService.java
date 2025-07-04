package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorRecordService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    public DoctorResponseDto createDoctor(DoctorCreateRequestDTO request) {

        Doctor doctor = doctorMapper.toEntity(request);

        Doctor savedDoctor = doctorRepository.save(doctor);

        List<DoctorSpecialtyDto> groupedSpecialties = groupSubSpecialtiesBySpecialty(savedDoctor.getSubSpecialties());


        return doctorMapper.toDoctorDetailsWithGroupedSpecialties(savedDoctor, groupedSpecialties);
    }

    public DoctorResponseDto updateDoctor(Long id, DoctorUpdateRequestDTO request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        doctorMapper.updateDoctorFromDto(request, doctor);

        Doctor updatedDoctor = doctorRepository.save(doctor);

        List<DoctorSpecialtyDto> groupedSpecialties = groupSubSpecialtiesBySpecialty(updatedDoctor.getSubSpecialties());


        return doctorMapper.toDoctorDetailsWithGroupedSpecialties(updatedDoctor, groupedSpecialties);
    }

    private List<DoctorSpecialtyDto> groupSubSpecialtiesBySpecialty(List<SubSpecialty> subSpecialties) {
        if (subSpecialties == null || subSpecialties.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Speciality, List<SubSpecialty>> grouped = subSpecialties.stream()
                .collect(Collectors.groupingBy(SubSpecialty::getSpeciality));

        List<DoctorSpecialtyDto> doctorSpecialtyDtos = new ArrayList<>();
        grouped.forEach((speciality, subs) -> {
            List<SubSpecialtyDetailsDto> subDetails = subs.stream()
                    .map(sub -> new SubSpecialtyDetailsDto(sub.getName(), sub.getCodeSubSpecialty()))
                    .collect(Collectors.toList());

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

