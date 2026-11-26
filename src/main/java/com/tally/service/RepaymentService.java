package com.tally.service;

import com.tally.account.AppUser;
import com.tally.account.AppUserRepository;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Repayment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recording and deleting payments members make to each other. Every method checks the user is in the group. */
@Service
@Transactional
public class RepaymentService {

    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;

    public RepaymentService(ExpenseGroupRepository groups, AppUserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    /** Records a payment and returns its id. Flushes so the id exists even before the transaction commits. */
    public long record(long userId, long groupId, RepaymentCommand command) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        AppUser recordedBy = users.getReferenceById(userId);
        Repayment repayment = group.recordRepayment(command.fromMemberId(), command.toMemberId(),
                command.amount(), command.date(), command.note(), recordedBy);
        groups.flush();
        return repayment.getId();
    }

    public void delete(long userId, long groupId, long repaymentId) {
        groups.getForUser(groupId, userId).removeRepayment(repaymentId);
    }
}
