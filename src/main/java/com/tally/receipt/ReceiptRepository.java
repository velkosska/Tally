package com.tally.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    @Query("""
            select new com.tally.receipt.ReceiptInfo(r.fileName, r.contentType, r.sizeBytes, r.uploadedAt)
            from Receipt r where r.expense.id = :expenseId
            """)
    Optional<ReceiptInfo> findInfo(@Param("expenseId") long expenseId);

    Optional<Receipt> findByExpenseId(long expenseId);

    /** Which expenses of a group have a receipt, for the paperclip on the group page. */
    @Query("select r.expense.id from Receipt r where r.expense.group.id = :groupId")
    List<Long> findExpenseIdsWithReceipt(@Param("groupId") long groupId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Receipt r where r.expense.id = :expenseId")
    int deleteByExpense(@Param("expenseId") long expenseId);
}
