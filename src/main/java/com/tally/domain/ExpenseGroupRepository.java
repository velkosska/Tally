package com.tally.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {

    List<ExpenseGroup> findAllByOrderByCreatedAtDesc();

    default ExpenseGroup getOrThrow(long groupId) {
        return findById(groupId).orElseThrow(() -> new NotFoundException("Group", groupId));
    }
}
