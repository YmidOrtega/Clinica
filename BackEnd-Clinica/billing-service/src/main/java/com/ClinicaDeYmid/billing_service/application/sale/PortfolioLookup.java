package com.ClinicaDeYmid.billing_service.application.sale;

import java.util.List;

public sealed interface PortfolioLookup {

    record Found(PortfolioService service) implements PortfolioLookup {
    }

    record Ambiguous(List<PortfolioService> candidates) implements PortfolioLookup {
    }

    record NotFound() implements PortfolioLookup {
    }

    record Unavailable() implements PortfolioLookup {
    }
}
