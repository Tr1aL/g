package org.tr1al.gainium.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.tr1al.gainium.dto.rest.SettingDto;
import org.tr1al.gainium.service.SettingService;

@RestController
@RequiredArgsConstructor
public class SettingController {

    private final SettingService service;

    @GetMapping("/settings")
    public SettingDto settings() {
        return service.getSetting();
    }

    @PostMapping("/settings")
    public SettingDto settingsPost(@RequestBody SettingDto settingDto) {
        return service.saveSetting(settingDto);
    }
}
