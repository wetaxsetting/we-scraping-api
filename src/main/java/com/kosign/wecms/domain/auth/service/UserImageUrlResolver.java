package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared resolver for per-user avatar URLs.
 *
 * <p>Batch-maps a set of user ids → the shared, non-admin streaming URL
 * {@code /api/v1/users/{userId}/image} for users that actually have a
 * stored image in {@code USER_BUSI_CONN_INFM.USER_IMG} (current tenant).
 * Users with no image are simply absent from the returned map, so the
 * caller ships a {@code null} URL and the frontend's shared
 * {@code <UserAvatar>} renders the initials disc.
 *
 * <p>This is the single home for the {@code USER_IMG → /api/v1/users/{id}/image}
 * logic across every approver / signer surface (Approval Box, Approval
 * Line, and the five Payment scheduled lists). The streaming endpoint it
 * points at is {@code UserController#streamImage}. Pre-existing private
 * copies inside {@code ApprovalLineService} / {@code ApprovalBoxService}
 * predate this resolver; new consumers MUST use this shared service rather
 * than clone the logic.
 */
@Service
@RequiredArgsConstructor
public class UserImageUrlResolver {

    /** Shared, non-admin streaming URL template (matches the URLs the
     *  Approval Line + notification paths already emit). */
    private static final String USER_IMAGE_URI_TEMPLATE = "/api/v1/users/%s/image";

    /** Active-link sentinel for {@code USER_BUSI_CONN_INFM.DEL_YN}. */
    private static final String DEL_YN_ACTIVE = "N";

    private final UserBusiConnInfmRepository userBusiRepo;

    /**
     * Resolve {@code user_img_url} for every supplied user id with a single
     * batched {@code findAllByUserIdInAndBizKeyAndDelYn}. Returns a map keyed
     * by user id; a user with no stored image is absent from the map.
     *
     * @param bizKey  current tenant
     * @param userIds approver / signer ids to resolve (deduplicated internally)
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolveImageUrls(String bizKey, Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        Set<String> distinct = new HashSet<>(userIds);
        List<UserBusiConnInfm> links =
                userBusiRepo.findAllByUserIdInAndBizKeyAndDelYn(distinct, bizKey, DEL_YN_ACTIVE);
        Map<String, String> out = new HashMap<>(links.size());
        for (UserBusiConnInfm link : links) {
            String image = link.getUserImg();
            if (image != null && !image.isBlank()) {
                out.put(link.getUserId(), String.format(USER_IMAGE_URI_TEMPLATE, link.getUserId()));
            }
        }
        return out;
    }
}
