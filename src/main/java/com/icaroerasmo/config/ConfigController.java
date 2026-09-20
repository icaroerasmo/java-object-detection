package com.icaroerasmo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public Map<String, Object> getConfig() {
        return configService.maskSecrets(configService.readConfig());
    }

    @PutMapping
    public ResponseEntity<Void> updateConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> current = configService.readConfig();
        Map<String, Object> merged = configService.restoreSecrets(config, current);
        configService.writeConfig(merged);

        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });
        shutdownThread.setDaemon(true);
        shutdownThread.start();

        return ResponseEntity.ok().build();
    }
}
