package com.ClinicaDeYmid.auth_service.infrastructure.password;

import com.ClinicaDeYmid.auth_service.domain.password.PasswordDenyList;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class ClasspathPasswordDenyList implements PasswordDenyList {

    private final Set<String> passwords;

    ClasspathPasswordDenyList(List<Resource> resources) {
        Set<String> loaded = new HashSet<>();
        for (Resource resource : resources) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(line -> line.strip().toLowerCase(Locale.ROOT))
                        .filter(line -> !line.isEmpty())
                        .forEach(loaded::add);
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not read the password deny list " + resource.getDescription(), ex);
            }
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("The password deny list is empty");
        }
        this.passwords = Set.copyOf(loaded);
    }

    @Override
    public boolean contains(String lowercasePassword) {
        return passwords.contains(lowercasePassword);
    }

    int size() {
        return passwords.size();
    }
}
