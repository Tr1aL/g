package org.tr1al.gainium.dto.gainium;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BotsResult {
    @JsonProperty("_id")
    private String id;
    private String userId;
    private String status;
    private List<BotSymbol> symbol;
    private String exchange;
    private UUID exchangeUUID;
    private Balances initialBalances;
    private Balances currentBalances;
    private UUID uuid;
    private ZonedDateTime updated;
    private Settings settings;
    private Deals deals;

}
