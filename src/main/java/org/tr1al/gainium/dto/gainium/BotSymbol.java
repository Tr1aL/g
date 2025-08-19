

package org.tr1al.gainium.dto.gainium;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BotSymbol {
    private String key;
    private BotSymbolValue value;
}
