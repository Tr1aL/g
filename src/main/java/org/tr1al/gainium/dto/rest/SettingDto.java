package org.tr1al.gainium.dto.rest;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.tr1al.gainium.entity.Setting;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class SettingDto {
    private Long botCount;
    private boolean paperContext;
    private boolean botArchiveEnabled;
    private boolean botLeaveEnabled;
    private BigDecimal botLeavePercent;
    private String botTemplateName;

    public SettingDto(Setting setting) {
        this.botCount = setting.getBotCount();
        this.paperContext = setting.isPaperContext();
        this.botLeaveEnabled = setting.isBotLeaveEnabled();
        this.botArchiveEnabled = setting.isBotArchiveEnabled();
        this.botLeavePercent = setting.getBotLeavePercent();
        this.botTemplateName = setting.getBotTemplateName();
    }

    public Setting fillSetting(Setting setting) {
        setting.setBotCount(this.botCount);
        setting.setPaperContext(this.paperContext);
        setting.setBotArchiveEnabled(this.botArchiveEnabled);
        setting.setBotLeaveEnabled(this.botLeaveEnabled);
        setting.setBotLeavePercent(this.botLeavePercent);
        setting.setBotTemplateName(this.botTemplateName);
        return setting;
    }
}
