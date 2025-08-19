package org.tr1al.gainium.dto.gainium;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealsResponse {
    private String status;
    private String reason;
    private DealsData data;
}
