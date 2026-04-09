package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.DeviceHealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP implementation of {@link DeviceHealthClient} using Spring's {@link RestClient}.
 * Times out after 5 s to avoid blocking the scheduler thread on unreachable devices.
 */
@Component
public class RestClientDeviceHealthClient implements DeviceHealthClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientDeviceHealthClient.class);

    private final RestClient restClient;

    public RestClientDeviceHealthClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<DeviceHealthResponse> fetchHealth(String ipAddress) {
        try {
            DeviceHealthResponse response = restClient.get()
                    .uri("http://" + ipAddress + "/health")
                    .retrieve()
                    .body(DeviceHealthResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.debug("Health check failed for {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }
}