package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatusService {

    private final UserRepository userRepository;

    public void ActivateUser(Long id) {

        log.info("Activating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + id + " no encontrado."));
        user.activate();
        userRepository.save(user);
        log.info("User with ID: {} activated successfully.", id);

    }

    public void DeactivateUser(Long id) {

        log.info("Deactivating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + id + " no encontrado."));
        user.deactivate();
        userRepository.save(user);
        log.info("User with ID: {} deactivated successfully.", id);

    }
}
