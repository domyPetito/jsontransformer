package com.petito.jsontransformer.controller;

import com.petito.jsontransformer.config.AppConfig;
import com.petito.jsontransformer.service.JsonTransformerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class JsonTransformerController {

    private final JsonTransformerService jsonTransformerService;
    private final AppConfig appConfig;
    private final RestTemplate restTemplate;

    public JsonTransformerController(JsonTransformerService jsonTransformerService, AppConfig appConfig) {
        this.jsonTransformerService = jsonTransformerService;
        this.appConfig = appConfig;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/transform")
    public ResponseEntity<Map<String, String>> transform() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Validazione OK");
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = "/commit",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> commit(@RequestBody(required = false) String inputJson) {
        System.out.println(inputJson);
        if (inputJson == null || inputJson.trim().isEmpty() || "null".equalsIgnoreCase(inputJson.trim())) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        Map<String, Object> transformed = jsonTransformerService.transformJson(inputJson);
        return ResponseEntity.ok(transformed != null ? transformed : Collections.emptyMap());
    }

    @PostMapping(
            value = "/send",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> send(@RequestBody(required = false) String inputJson) {
        if (inputJson == null || inputJson.trim().isEmpty() || "null".equalsIgnoreCase(inputJson.trim())) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        Map<String, Object> transformed = jsonTransformerService.transformJson(inputJson);
        // Usiamo una HashMap mutabile per poter inserire "serverResponse" senza errori a runtime
        Map<String, Object> payloadToSend = (transformed != null) ? new HashMap<>(transformed) : new HashMap<>();

        Map<String, Object> responseStatus = new HashMap<>();
        try {
            String serverUrl = appConfig.getFullServerUrl();
            ResponseEntity<String> serverResponse = restTemplate.postForEntity(serverUrl, payloadToSend, String.class);
            responseStatus.put("status", "success");
            responseStatus.put("serverResponse", serverResponse.getBody());
        } catch (Exception e) {
            responseStatus.put("status", "error");
            responseStatus.put("message", e.getMessage());
        }

        payloadToSend.put("serverResponse", responseStatus);
        return ResponseEntity.ok(payloadToSend);
    }

    @PostMapping(
            value = "/testSend",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> testSend(@RequestBody Map<String, Object> inputJson) {
        Map<String, Object> response = new HashMap<>();
        try {
            String remoteUrl = appConfig.getFullServerUrl();
            ResponseEntity<String> remoteResponse = restTemplate.postForEntity(remoteUrl, inputJson, String.class);

            response.put("status", "success");
            response.put("remoteUrl", remoteUrl);
            response.put("remoteResponse", remoteResponse.getBody());
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}