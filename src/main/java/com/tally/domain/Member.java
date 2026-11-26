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

/**
 * A person taking part in a group. Created only through {@link ExpenseGroup#addMember}.
 * A member may be linked to a user account; only linked users can open the group.
 */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    protected Member() {
        // for JPA
    }

    Member(ExpenseGroup group, String name, AppUser user) {
        this.group = group;
        this.name = name;
        this.user = user;
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

    /** The linked account, or {@code null} for someone who only exists as a name in this group. */
    public AppUser getUser() {
        return user;
    }

    public boolean hasAccount() {
        return user != null;
    }

    public boolean isLinkedTo(long userId) {
        return user != null && user.getId() == userId;
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
