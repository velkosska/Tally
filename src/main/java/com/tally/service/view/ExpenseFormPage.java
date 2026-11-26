package com.tally.service.view;

import java.util.List;

/** What the add/edit expense page needs besides the form itself. */
public record ExpenseFormPage(long groupId, String groupName, String currency, List<MemberOption> members) {
}
