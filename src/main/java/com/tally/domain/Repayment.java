package com.tally.domain;

import com.tally.account.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Money one member paid back to another, for example following the settle-up plan.
 * Created only through {@link ExpenseGroup#recordRepayment}.
 */
@Entity
@Table(name = "repayment")
public class Repayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id")
    private Member from;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_member_id")
    private Member to;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate date;

    @Column(length = 120)
    private String note;

    /** Who wrote it down, which isn't always the person who paid. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Repayment() {
        // for JPA
    }

    Repayment(ExpenseGroup group, Member from, Member to, BigDecimal amount, LocalDate date, String note,
              AppUser createdBy) {
        this.group = group;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.date = date;
        this.note = note == null || note.isBlank() ? null : note.strip();
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public boolean involves(Member member) {
        return from.equals(member) || to.equals(member);
    }

    public Long getId() {
        return id;
    }

    public Member getFrom() {
        return from;
    }

    public Member getTo() {
        return to;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getNote() {
        return note;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
