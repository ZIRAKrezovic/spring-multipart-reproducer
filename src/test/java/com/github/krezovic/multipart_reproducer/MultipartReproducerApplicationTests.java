package com.github.krezovic.multipart_reproducer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultipartReproducerApplicationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private RestClient.Builder loadBalancedRestClientBuilder;

    @Autowired
    private RestTemplate restTemplate;

    @Test
    void restTemplateTests() throws IOException {
        var resource = new FileSystemResource(randomLargeFile());
        var requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        var requestBody = new LinkedMultiValueMap<String, Object>();
        requestBody.set("file", resource);

        var entity = new HttpEntity<>(requestBody, requestHeaders);

        log.info("Interceptors found count: {}", restTemplate.getInterceptors().size());

        // comment this out ... StreamingHttpOutputMessage will be detected in FormHttpMessageConverter::writeMultipart
        Assertions.assertDoesNotThrow(() -> restTemplate.exchange("http://multipart-reproducer/upload", HttpMethod.POST, entity, Void.class));

        var emptyRestTemplate = restTemplateBuilder.build();

        log.info("Interceptors found count: {}", emptyRestTemplate.getInterceptors().size());

        Assertions.assertDoesNotThrow(() -> emptyRestTemplate.exchange(String.format("http://localhost:%d/upload", port), HttpMethod.POST, entity, Void.class));
    }

    @Test
    void restClientTests() throws IOException {
        var resource = new FileSystemResource(randomLargeFile());
        var requestBody = new LinkedMultiValueMap<String, Object>();
        requestBody.set("file", resource);

        loadBalancedRestClientBuilder.requestInterceptors(i -> log.info("Interceptors found count: {}", i.size()));

        Assertions.assertDoesNotThrow(() -> postRestClient(loadBalancedRestClientBuilder.build(), "http://multipart-reproducer/upload", requestBody));

        var builder = RestClient.builder();

        // Even without any interceptors, it will still use non streaming variant in FormHttpMessageConverter::writeMultipart
        builder.requestInterceptors(i -> log.info("Interceptors found count: {}", i.size()));

        Assertions.assertDoesNotThrow(() -> postRestClient(builder.build(), String.format("http://localhost:%d/upload", port), requestBody));
    }

    private RestClient.ResponseSpec postRestClient(RestClient client, String uri, MultiValueMap<String, Object> body) {
        return client.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve();
    }

    private Path randomLargeFile() throws IOException {
        Path path = Files.createTempFile("test", "bin");
        SecureRandom random = new SecureRandom();

        try (var channel = Files.newByteChannel(path, StandardOpenOption.APPEND)) {
            long i = 0;
            byte[] buf = new byte[1 << 16];

            while (i < 368_709_120L) {
                random.nextBytes(buf);

                channel.position(i);
                channel.write(ByteBuffer.wrap(buf));

                i += buf.length;
            }
        }

        return path;
    }

    @TestConfiguration
    static class TestConfigurationComponent {
        @Bean
        @LoadBalanced
        public RestTemplate restTemplate(RestTemplateBuilder builder) {
            return builder.build();
        }

        @Bean
        @LoadBalanced
        public RestClient.Builder loadBalancedRestClientBuilder() {
            return RestClient.builder();
        }
    }

    @TestConfiguration
    static class DiscoveryClientConfiguration {
        private SimpleDiscoveryProperties properties;

        @Autowired
        public void setProperties(SimpleDiscoveryProperties properties) {
            this.properties = properties;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void setup() {
            var local = properties.getLocal();
            local.setHost("localhost");
            properties.setInstances(Map.of(local.getServiceId(), List.of(local)));
        }
    }
}
