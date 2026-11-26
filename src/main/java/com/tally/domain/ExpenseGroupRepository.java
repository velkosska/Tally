package com.tally.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {

    /** The groups this user is a member of, newest first. */
    @Query("""
            select g from ExpenseGroup g
            where exists (select 1 from Member m where m.group = g and m.user.id = :userId)
            order by g.createdAt desc
            """)
    List<ExpenseGroup> findAllForUser(@Param("userId") long userId);

    @Query("""
            select g from ExpenseGroup g
            where g.id = :groupId
              and exists (select 1 from Member m where m.group = g and m.user.id = :userId)
            """)
    Optional<ExpenseGroup> findForUser(@Param("groupId") long groupId, @Param("userId") long userId);

    /**
     * Loads a group only if the user is one of its members. Every service goes through this,
     * so a group someone isn't in looks exactly like a group that doesn't exist (404, not 403),
     * and nobody can find out which group ids exist by guessing.
     */
    default ExpenseGroup getForUser(long groupId, long userId) {
        return findForUser(groupId, userId).orElseThrow(() -> new NotFoundException("Group", groupId));
    }
}
