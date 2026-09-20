package com.icaroerasmo.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Service
public class ConfigService {

    private static final Pattern URL_CREDENTIAL_PATTERN = Pattern.compile("^(\\w+://[^:/@]+:)([^@]+)(@.*)$");

    private static final String MASK = "********";

    @Value("${app.config-file-path:/app/config/config.yaml}")
    private String configFilePath;

    public Map<String, Object> readConfig() {
        try (FileReader reader = new FileReader(configFilePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(reader);
            return config != null ? config : new LinkedHashMap<>();
        } catch (IOException e) {
            log.error("Failed to read config file: {}", configFilePath, e);
            return new LinkedHashMap<>();
        }
    }

    public void writeConfig(Map<String, Object> config) {
        try (FileWriter writer = new FileWriter(configFilePath)) {
            Yaml yaml = new Yaml();
            yaml.dump(config, writer);
        } catch (IOException e) {
            log.error("Failed to write config file: {}", configFilePath, e);
            throw new RuntimeException("Failed to write config", e);
        }
    }

    public Map<String, Object> maskSecrets(Map<String, Object> config) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            masked.put(entry.getKey(), maskValue(entry.getKey(), entry.getValue()));
        }
        return masked;
    }

    private Object maskValue(String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            return maskSecrets((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return maskList(list);
        }
        if (isSecretKey(key)) {
            return MASK;
        }
        if (value instanceof String str) {
            Matcher matcher = URL_CREDENTIAL_PATTERN.matcher(str);
            if (matcher.matches()) {
                return matcher.group(1) + MASK + matcher.group(3);
            }
        }
        return value;
    }

    private List<Object> maskList(List<?> list) {
        List<Object> masked = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                masked.add(maskSecrets((Map<String, Object>) map));
            } else {
                masked.add(item);
            }
        }
        return masked;
    }

    private boolean isSecretKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("password") || lower.contains("token") || lower.contains("secret")
                || lower.contains("credential") || lower.endsWith("key");
    }

    public Map<String, Object> restoreSecrets(Map<String, Object> incoming, Map<String, Object> current) {
        Map<String, Object> restored = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            Object incomingValue = entry.getValue();
            Object currentValue = current.get(key);
            restored.put(key, restoreValue(incomingValue, currentValue));
        }
        return restored;
    }

    private Object restoreValue(Object incomingValue, Object currentValue) {
        if (incomingValue instanceof Map<?, ?> incomingMap) {
            if (currentValue instanceof Map<?, ?> currentMap) {
                return restoreSecrets((Map<String, Object>) incomingMap, (Map<String, Object>) currentMap);
            }
            return maskSecrets((Map<String, Object>) incomingMap);
        }
        if (incomingValue instanceof List<?> incomingList) {
            if (currentValue instanceof List<?> currentList) {
                return restoreList(incomingList, currentList);
            }
            return maskList(incomingList);
        }
        if (MASK.equals(incomingValue)) {
            return currentValue;
        }
        if (incomingValue instanceof String str && str.contains(":********@")) {
            return currentValue;
        }
        return incomingValue;
    }

    @SuppressWarnings("unchecked")
    private List<Object> restoreList(List<?> incomingList, List<?> currentList) {
        List<Object> restored = new ArrayList<>();
        for (int i = 0; i < incomingList.size(); i++) {
            Object incomingItem = incomingList.get(i);
            Object currentItem = i < currentList.size() ? currentList.get(i) : null;
            if (incomingItem instanceof Map<?, ?> incomingMap) {
                if (currentItem instanceof Map<?, ?> currentMap) {
                    restored.add(restoreSecrets((Map<String, Object>) incomingMap, (Map<String, Object>) currentMap));
                } else {
                    restored.add(maskSecrets((Map<String, Object>) incomingMap));
                }
            } else {
                restored.add(restoreValue(incomingItem, currentItem));
            }
        }
        return restored;
    }
}
