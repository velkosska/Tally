package com.tally.domain;

import com.tally.domain.Expense.ExpenseDetails;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;

/**
 * A group of people sharing costs, such as a trip or a flat. This is the aggregate root:
 * members and expenses are only ever changed through it, so its rules are always enforced.
 */
@Entity
@Table(name = "tally_group")
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("date DESC, id DESC")
    private List<Expense> expenses = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Member> members = new ArrayList<>();

    protected ExpenseGroup() {
        // for JPA
    }

    public ExpenseGroup(String name, String description, String currency) {
        this.createdAt = Instant.now();
        updateDetails(name, description, currency);
    }

    public void updateDetails(String name, String description, String currency) {
        this.name = name.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.currency = Currency.getInstance(currency).getCurrencyCode();
    }

    // ---- members ----

    public Member addMember(String memberName) {
        String cleanName = memberName.strip();
        members.stream()
                .filter(m -> m.getName().equalsIgnoreCase(cleanName))
                .findFirst()
                .ifPresent(existing -> {
                    throw new BusinessRuleException(existing.getName() + " is already in this group.");
                });
        Member member = new Member(this, cleanName);
        members.add(member);
        return member;
    }

    public void removeMember(long memberId) {
        Member member = findMember(memberId);
        if (expenses.stream().anyMatch(expense -> expense.involves(member))) {
            throw new BusinessRuleException(member.getName()
                    + " is part of existing expenses. Remove or edit those expenses first.");
        }
        members.remove(member);
    }

    public Member findMember(long memberId) {
        return members.stream()
                .filter(m -> m.getId() != null && m.getId() == memberId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    // ---- expenses ----

    public Expense addExpense(ExpenseDetails details) {
        checkBelongsToGroup(details);
        Expense expense = new Expense(this, details);
        expenses.add(expense);
        return expense;
    }

    public void updateExpense(long expenseId, ExpenseDetails details) {
        checkBelongsToGroup(details);
        findExpense(expenseId).apply(details);
    }

    public void removeExpense(long expenseId) {
        expenses.remove(findExpense(expenseId));
    }

    /** Deletes every expense; used before deleting the group so rows go away in a safe order. */
    public void removeAllExpenses() {
        expenses.clear();
    }

    public Expense findExpense(long expenseId) {
        return expenses.stream()
                .filter(e -> e.getId() != null && e.getId() == expenseId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Expense", expenseId));
    }

    private void checkBelongsToGroup(ExpenseDetails details) {
        if (details.participants().isEmpty()) {
            throw new BusinessRuleException("Pick at least one person to split the expense between.");
        }
        boolean allMembers = members.contains(details.paidBy())
                && members.containsAll(details.participants());
        if (!allMembers) {
            throw new BusinessRuleException("Everyone on an expense must be a member of this group.");
        }
    }

    // ---- getters ----

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Member> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }
}
