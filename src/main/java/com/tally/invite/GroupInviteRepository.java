package com.tally.invite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    Optional<GroupInvite> findByTokenHash(String tokenHash);

    @Query("""
            select count(i) from GroupInvite i
            where i.group.id = :groupId and i.revokedAt is null and i.expiresAt > :now
            """)
    long countActive(@Param("groupId") long groupId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GroupInvite i set i.revokedAt = :now
            where i.group.id = :groupId and i.revokedAt is null
            """)
    int revokeAll(@Param("groupId") long groupId, @Param("now") Instant now);
}
