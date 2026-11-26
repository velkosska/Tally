package com.tally.domain;

import com.tally.settlement.InvalidSplitException;
import com.tally.settlement.SplitCalculator;
import com.tally.settlement.SplitMethod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyJoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Something one member paid for, split between the participants equally or unevenly. */
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "split_method", nullable = false, length = 10)
    private SplitMethod splitMethod = SplitMethod.EQUAL;

    /**
     * Who shares the cost, with their number for the split method. For an equal split everyone has 1
     * (never null: Hibernate doesn't store null values in a collection, so the row would be lost).
     */
    @ElementCollection
    @CollectionTable(name = "expense_participant", joinColumns = @JoinColumn(name = "expense_id"))
    @MapKeyJoinColumn(name = "member_id")
    @Column(name = "weight", nullable = false, precision = 12, scale = 2)
    private Map<Member, BigDecimal> participants = new HashMap<>();

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
        this.splitMethod = details.splitMethod();
        this.participants.clear();
        details.participants().forEach((member, weight) ->
                participants.put(member, splitMethod.usesWeights() ? weight : BigDecimal.ONE));
    }

    /**
     * Checks that the split works for this amount, for example that percentages add up to 100.
     * Called by the group before an expense is added or changed.
     */
    static void checkSplit(ExpenseDetails details) {
        Map<Long, BigDecimal> weights = new HashMap<>();
        details.participants().forEach((member, weight) -> weights.put(member.getId(), weight));
        try {
            SplitCalculator.split(details.amount().movePointRight(2).longValue(), details.splitMethod(), weights);
        } catch (InvalidSplitException e) {
            throw new BusinessRuleException(e.getMessage());
        }
    }

    public boolean involves(Member member) {
        return paidBy.equals(member) || participants.containsKey(member);
    }

    /** Each participant's share in money, in name order; always adds up to the amount. */
    public Map<Member, BigDecimal> shares() {
        Map<Long, BigDecimal> weights = new HashMap<>();
        participants.forEach((member, weight) -> weights.put(member.getId(), weight));
        Map<Long, Long> cents = SplitCalculator.split(
                amount.movePointRight(2).longValueExact(), splitMethod, weights);
        Map<Member, BigDecimal> shares = new LinkedHashMap<>();
        getParticipants().forEach(m -> shares.put(m, BigDecimal.valueOf(cents.get(m.getId()), 2)));
        return shares;
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

    public SplitMethod getSplitMethod() {
        return splitMethod;
    }

    /** The participants in name order. */
    public Set<Member> getParticipants() {
        return participants.keySet().stream()
                .sorted(Comparator.comparing(Member::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Each participant's number for the split method: amount, percentage or shares (1 for an equal split). */
    public Map<Member, BigDecimal> getWeights() {
        return Collections.unmodifiableMap(participants);
    }

    /**
     * Everything needed to create or update an expense, validated by the owning group.
     *
     * @param participants who shares the cost, with their number for the split method (ignored when equal)
     */
    public record ExpenseDetails(String description, BigDecimal amount, Category category,
                                 LocalDate date, Member paidBy, SplitMethod splitMethod,
                                 Map<Member, BigDecimal> participants) {
    }
}
