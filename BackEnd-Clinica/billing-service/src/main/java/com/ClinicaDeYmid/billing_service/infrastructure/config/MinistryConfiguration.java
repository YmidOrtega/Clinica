package com.ClinicaDeYmid.billing_service.infrastructure.config;

import com.ClinicaDeYmid.billing_service.application.filing.FilingPolicy;
import com.ClinicaDeYmid.billing_service.application.objection.ObjectionPolicy;
import com.ClinicaDeYmid.billing_service.application.rips.MinistryValidator;
import com.ClinicaDeYmid.billing_service.infrastructure.ministry.MinistryCredentials;
import com.ClinicaDeYmid.billing_service.infrastructure.ministry.RestMinistryValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class MinistryConfiguration {

    @Bean
    ObjectionPolicy objectionPolicy(
            @Value("${clinica.billing.objections.warning-business-days}") int warningBusinessDays) {
        return new ObjectionPolicy(warningBusinessDays);
    }

    @Bean
    FilingPolicy filingPolicy(@Value("${clinica.billing.filing.warning-business-days}") int warningBusinessDays) {
        return new FilingPolicy(warningBusinessDays);
    }

    @Bean
    MinistryValidator ministryValidator(@Value("${clinica.billing.ministry.url}") String url,
                                        @Value("${clinica.billing.ministry.document-type}") String documentType,
                                        @Value("${clinica.billing.ministry.document-number}") String documentNumber,
                                        @Value("${clinica.billing.ministry.password}") String password,
                                        @Value("${clinica.billing.ministry.connect-timeout}") Duration connectTimeout,
                                        @Value("${clinica.billing.ministry.read-timeout}") Duration readTimeout,
                                        Clock clock) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requests.setReadTimeout(readTimeout);
        return new RestMinistryValidator(RestClient.builder().baseUrl(url).requestFactory(requests).build(),
                new MinistryCredentials(documentType, documentNumber, password), clock);
    }
}
