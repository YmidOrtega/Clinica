package com.ClinicaDeYmid.billing_service.application.context;

public sealed interface EpisodeLookup {

    record Found(EpisodeDetails episode) implements EpisodeLookup {
    }

    record NotFound() implements EpisodeLookup {
    }

    record Unavailable() implements EpisodeLookup {
    }
}
