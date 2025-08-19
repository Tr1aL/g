package org.tr1al.gainium.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@NoArgsConstructor
@Entity
@Data
public class Bot {
    @Id
    @Column(name = "bot_id")
    private String botId;
    private String symbol;
    private Instant created;
    private BigDecimal adts;
    private BigDecimal adtv;
}
