package com.ClinicaDeYmid.billing_service.application.context;

import java.util.UUID;

public interface EpisodeDirectory {

    EpisodeLookup episode(UUID admissionUuid);
}
