package com.tally.invite;

import com.tally.account.AppUser;
import com.tally.domain.ExpenseGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A share link that lets people join a group. The link itself is never stored, only a hash of its
 * secret token, so the database alone can't be used to join groups.
 */
@Entity
@Table(name = "group_invite")
public class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private ExpenseGroup group;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected GroupInvite() {
        // for JPA
    }

    GroupInvite(ExpenseGroup group, String tokenHash, AppUser createdBy, Instant createdAt, Instant expiresAt) {
        this.group = group;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public ExpenseGroup getGroup() {
        return group;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
