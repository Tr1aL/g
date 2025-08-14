package org.tr1al.gainium.dto.nats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NatsResponse {

    private long timestamp;
    private List<NatsData> data;
}
