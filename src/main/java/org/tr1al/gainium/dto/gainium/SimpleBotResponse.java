package org.tr1al.gainium.dto.gainium;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleBotResponse {

    private String status;
    private String reason;
    private Object data;
}
