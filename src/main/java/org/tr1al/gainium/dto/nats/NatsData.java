package org.tr1al.gainium.dto.nats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NatsData {

    private String symbol;
    private BigDecimal adtv;
    private BigDecimal adts;
}
