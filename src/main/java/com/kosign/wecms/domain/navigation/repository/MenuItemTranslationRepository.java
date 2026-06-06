package com.kosign.wecms.domain.navigation.repository;

import com.kosign.wecms.domain.navigation.entity.MenuItemTranslation;
import com.kosign.wecms.domain.navigation.entity.MenuItemTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link MenuItemTranslation}. The navigation service loads
 * the caller's language (and EN as the fallback) for the resolved product
 * and builds an in-memory {@code menuItemId -> label} map.
 */
public interface MenuItemTranslationRepository
        extends JpaRepository<MenuItemTranslation, MenuItemTranslationId> {

    /** All labels for a product in one language. */
    List<MenuItemTranslation> findAllByProductTypeAndLangCd(String productType, String langCd);
}
