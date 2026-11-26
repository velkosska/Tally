package com.tally.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Something one member paid for, shared equally between the participants. */
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private ExpenseGroup group;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "spent_on", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id")
    private Member paidBy;

    @ManyToMany
    @JoinTable(name = "expense_participant",
            joinColumns = @JoinColumn(name = "expense_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id"))
    @OrderBy("name ASC")
    private Set<Member> participants = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Expense() {
        // for JPA
    }

    Expense(ExpenseGroup group, ExpenseDetails details) {
        this.group = group;
        this.createdAt = Instant.now();
        apply(details);
    }

    void apply(ExpenseDetails details) {
        this.description = details.description().strip();
        this.amount = details.amount();
        this.category = details.category();
        this.date = details.date();
        this.paidBy = details.paidBy();
        this.participants.clear();
        this.participants.addAll(details.participants());
    }

    public boolean involves(Member member) {
        return paidBy.equals(member) || participants.contains(member);
    }

    public Long getId() {
        return id;
    }

    public ExpenseGroup getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getDate() {
        return date;
    }

    public Member getPaidBy() {
        return paidBy;
    }

    public Set<Member> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    /** Everything needed to create or update an expense, validated by the owning group. */
    public record ExpenseDetails(String description, BigDecimal amount, Category category,
                                 LocalDate date, Member paidBy, Collection<Member> participants) {
    }
}
