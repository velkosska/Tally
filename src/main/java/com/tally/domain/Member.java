package com.tally.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A person taking part in a group. Created only through {@link ExpenseGroup#addMember(String)}. */
@Entity
@Table(name = "group_member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private ExpenseGroup group;

    @Column(nullable = false, length = 50)
    private String name;

    protected Member() {
        // for JPA
    }

    Member(ExpenseGroup group, String name) {
        this.group = group;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public ExpenseGroup getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    /** Identity is the database id, which also works when Hibernate hands us a lazy proxy. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Member that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Member.class.hashCode();
    }
}
