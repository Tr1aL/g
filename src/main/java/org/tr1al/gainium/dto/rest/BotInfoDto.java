package org.tr1al.gainium.dto.rest;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class BotInfoDto {
    private String botId;
    private String symbol;
    private BigDecimal startAdts;
    private BigDecimal currentAdts;
    private String source;
}
