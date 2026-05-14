package com.kosign.wecms.domain.setting.users.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite-PK class for {@link UserMenuAuthInfm} — required by JPA
 * when an entity uses {@code @IdClass} with multiple {@code @Id}
 * fields. Field names + types must match the entity's {@code @Id}
 * declarations exactly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserMenuAuthInfmId implements Serializable {
    private String bizKey;
    private String userId;
    private String menuAuthCd;
}
