package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserSummaryDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;



@Slf4j
@Service
@RequiredArgsConstructor
public class UserGetService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + id + " no encontrado."));

        return userMapper.toUserResponseDTO(user);
    }

    public Page<UserSummaryDTO> getUserSummaryPage(Pageable pagination) {

        Page<User> usersPage = userRepository.findByActiveTrue(pagination);

        return usersPage.map(userMapper::toUserSummaryDTO);
    }

}
