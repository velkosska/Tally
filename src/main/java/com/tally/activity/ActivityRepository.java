package com.tally.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /** A group's feed, newest first. The caller checks the user is in the group. */
    @Query(value = """
            select a from Activity a join fetch a.group
            where a.group.id = :groupId
            order by a.createdAt desc, a.id desc
            """,
            countQuery = "select count(a) from Activity a where a.group.id = :groupId")
    Page<Activity> findForGroup(@Param("groupId") long groupId, Pageable page);

    /** Removes a group's feed before the group itself is deleted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Activity a where a.group.id = :groupId")
    void deleteForGroup(@Param("groupId") long groupId);

    /** The latest changes across every group this user is a member of. */
    @Query("""
            select a from Activity a join fetch a.group g
            where exists (select 1 from Member m where m.group = g and m.user.id = :userId)
            order by a.createdAt desc, a.id desc
            """)
    List<Activity> findLatestForUser(@Param("userId") long userId, Pageable page);
}
