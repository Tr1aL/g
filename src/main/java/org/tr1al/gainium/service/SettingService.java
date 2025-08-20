package org.tr1al.gainium.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tr1al.gainium.dto.rest.SettingDto;
import org.tr1al.gainium.entity.Setting;
import org.tr1al.gainium.repository.SettingRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingService {

    private final SettingRepository settingRepository;

    public SettingDto getSetting() {
        return settingRepository.findById(Setting.SETTING_ID)
                .map(SettingDto::new)
                .orElseThrow(() -> new RuntimeException("setting with id " + Setting.SETTING_ID + " not found"));
    }

    public SettingDto saveSetting(SettingDto settingDto) {
        Setting setting = settingRepository.findById(Setting.SETTING_ID)
                .orElseThrow(() -> new RuntimeException("setting with id " + Setting.SETTING_ID + " not found"));
        setting = settingDto.fillSetting(setting);
        setting = settingRepository.save(setting);
        return new SettingDto(setting);
    }
}
