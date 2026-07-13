package com.kosign.wecms.domain.payment.internaltransfer.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite key for {@link CutoffConfig} — {@code (BANK_KEY, NETWORK)}.
 * Field names must match the {@code @Id} fields on the entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CutoffConfigId implements Serializable {

    private String bankKey;
    private String network;
}
