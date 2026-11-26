package com.tally.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c from Comment c left join fetch c.author where c.expense.id = :expenseId order by c.createdAt, c.id")
    List<Comment> findForExpense(@Param("expenseId") long expenseId);

    /** Comment counts per expense of a group, as [expenseId, count] rows, for the group page. */
    @Query("select c.expense.id, count(c) from Comment c where c.expense.group.id = :groupId group by c.expense.id")
    List<Object[]> countByExpense(@Param("groupId") long groupId);
}
