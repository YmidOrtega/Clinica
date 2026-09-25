package com.ClinicaDeYmid.billing_service.application.sale;

import java.util.UUID;

public interface PortfolioCatalogue {

    PortfolioLookup byUuid(UUID portfolioItemUuid);

    PortfolioLookup byCups(String cupsCode);
}
