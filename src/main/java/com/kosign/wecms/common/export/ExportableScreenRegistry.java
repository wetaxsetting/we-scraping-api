package com.kosign.wecms.common.export;

import com.kosign.wecms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup table of {@link ExportRowProvider}s keyed by
 * {@code screenId}. Spring auto-wires every {@link
 * org.springframework.stereotype.Component @Component} implementation of
 * the provider interface; the registry rejects duplicate registrations
 * at startup so two strategies cannot accidentally claim the same
 * screen.
 *
 * <p>{@link #require(String)} throws {@code WBE2010} for unknown
 * screenIds — same envelope shape as every other {@link BusinessException}.
 * The frontend surfaces it through the standard system dialog.
 */
@Component
public class ExportableScreenRegistry {

    static final String UNKNOWN_SCREEN = "WBE2010";

    private final Map<String, ExportRowProvider> byScreenId;

    public ExportableScreenRegistry(List<ExportRowProvider> providers) {
        Map<String, ExportRowProvider> map = new HashMap<>();
        for (ExportRowProvider p : providers) {
            String id = p.screenId();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        "ExportRowProvider " + p.getClass().getName() + " returned a null/blank screenId");
            }
            ExportRowProvider prev = map.putIfAbsent(id, p);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate ExportRowProvider for screenId '" + id + "': "
                                + prev.getClass().getName() + " vs. " + p.getClass().getName());
            }
        }
        this.byScreenId = Map.copyOf(map);
    }

    /**
     * Resolve the provider for {@code screenId}. Throws
     * {@code WBE2010} for unknown ids. Use this from the per-resource
     * controller's export endpoint.
     */
    public ExportRowProvider require(String screenId) {
        ExportRowProvider p = byScreenId.get(screenId);
        if (p == null) {
            throw new BusinessException(UNKNOWN_SCREEN, screenId);
        }
        return p;
    }
}
