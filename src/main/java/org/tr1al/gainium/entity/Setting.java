package org.tr1al.gainium.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@Entity
@Data
public class Setting {

    public final static Integer SETTING_ID = 1;
    @Id
    private Integer id;
    private Long botCount;
    private boolean paperContext;
    private BigDecimal botLeavePercent;
    private String botTemplateName;
}
