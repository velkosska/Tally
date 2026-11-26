package com.tally.activity;

import com.tally.account.AppUser;
import com.tally.domain.ExpenseGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** One entry in a group's activity feed. Never changed after it is written. */
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", updatable = false)
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private AppUser actor;

    @Column(name = "actor_name", nullable = false, length = 50, updatable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private ActivityType type;

    @Column(length = 120, updatable = false)
    private String subject;

    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 400, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Activity() {
        // for JPA
    }

    Activity(ExpenseGroup group, AppUser actor, ActivityType type, String subject, BigDecimal amount,
             String details) {
        this.group = group;
        this.actor = actor;
        this.actorName = actor.getDisplayName();
        this.type = type;
        this.subject = truncate(subject, 120);
        this.amount = amount;
        this.details = truncate(details, 400);
        this.createdAt = Instant.now();
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public ExpenseGroup getGroup() {
        return group;
    }

    public AppUser getActor() {
        return actor;
    }

    public String getActorName() {
        return actorName;
    }

    public ActivityType getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
