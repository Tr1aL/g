package org.tr1al.gainium.dto.gainium;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealsResult {
    @JsonProperty("_id")
    private String id;
    private String botId;
    private String userId;
    private String status;
    private DealSymbol symbol;
}
