package com.tally.service;

import com.tally.domain.Category;
import com.tally.domain.Expense;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.domain.Repayment;
import com.tally.service.view.CategoryTotal;
import com.tally.service.view.ExpenseRow;
import com.tally.service.view.GroupDashboard;
import com.tally.service.view.MemberBalance;
import com.tally.service.view.MemberOption;
import com.tally.service.view.Payment;
import com.tally.service.view.RepaymentRow;
import com.tally.settlement.BalanceCalculator;
import com.tally.settlement.ExpenseShare;
import com.tally.settlement.SettlementCalculator;
import com.tally.settlement.Transfer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the group page: balances, the settle-up plan, category totals and the expense list. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ExpenseGroupRepository groups;

    public DashboardService(ExpenseGroupRepository groups) {
        this.groups = groups;
    }

    public GroupDashboard dashboard(long userId, long groupId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        List<Member> members = group.getMembers();
        List<Expense> expenses = group.getExpenses();
        Map<Long, String> names = members.stream().collect(Collectors.toMap(Member::getId, Member::getName));

        List<Repayment> repayments = group.getRepayments();
        List<Transfer> paymentsMade = repayments.stream()
                .map(r -> new Transfer(r.getFrom().getId(), r.getTo().getId(), r.getAmount()))
                .toList();
        Map<Long, BigDecimal> net = BalanceCalculator.netBalances(names.keySet(), toShares(expenses), paymentsMade);
        BigDecimal total = sum(expenses.stream().map(Expense::getAmount).toList());

        return new GroupDashboard(
                group.getId(), group.getName(), group.getDescription(), group.getCurrency(),
                total, average(total, members.size()),
                memberOptions(members, expenses, repayments, userId),
                balances(members, expenses, paymentsMade, net),
                settlement(net, names),
                categoryTotals(expenses, total),
                expenses.stream().map(DashboardService::toRow).toList(),
                repayments.stream().map(DashboardService::toRepaymentRow).toList());
    }

    private static List<ExpenseShare> toShares(List<Expense> expenses) {
        return expenses.stream()
                .map(e -> {
                    Map<Long, BigDecimal> weights = new HashMap<>();
                    e.getWeights().forEach((m, w) -> weights.put(m.getId(), w));
                    return new ExpenseShare(e.getPaidBy().getId(), e.getAmount(), e.getSplitMethod(), weights);
                })
                .toList();
    }

    private static List<MemberOption> memberOptions(List<Member> members, List<Expense> expenses,
                                                    List<Repayment> repayments, long userId) {
        return members.stream()
                .map(m -> new MemberOption(m.getId(), m.getName(),
                        !m.hasAccount() && expenses.stream().noneMatch(e -> e.involves(m))
                                && repayments.stream().noneMatch(r -> r.involves(m)),
                        m.hasAccount(), m.isLinkedTo(userId)))
                .toList();
    }

    private static List<MemberBalance> balances(List<Member> members, List<Expense> expenses,
                                                List<Transfer> paymentsMade, Map<Long, BigDecimal> net) {
        Map<Long, BigDecimal> repaid = new HashMap<>();
        for (Transfer payment : paymentsMade) {
            repaid.merge(payment.fromId(), payment.amount(), BigDecimal::add);
            repaid.merge(payment.toId(), payment.amount().negate(), BigDecimal::add);
        }
        Map<Long, BigDecimal> paid = expenses.stream().collect(Collectors.groupingBy(
                e -> e.getPaidBy().getId(),
                Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        BigDecimal largest = net.values().stream()
                .map(BigDecimal::abs)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        return members.stream()
                .map(m -> {
                    BigDecimal memberPaid = paid.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(2);
                    BigDecimal memberRepaid = repaid.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(2);
                    BigDecimal memberNet = net.get(m.getId());
                    // net = paid - share + repaid, so the share is what's left over
                    BigDecimal share = memberPaid.add(memberRepaid).subtract(memberNet);
                    return new MemberBalance(m.getId(), m.getName(), memberPaid, share, memberRepaid,
                            memberNet, percentOf(memberNet.abs(), largest));
                })
                .sorted(Comparator.comparing(MemberBalance::net).reversed())
                .toList();
    }

    private static List<Payment> settlement(Map<Long, BigDecimal> net, Map<Long, String> names) {
        return SettlementCalculator.settle(net).stream()
                .map((Transfer t) -> new Payment(t.fromId(), names.get(t.fromId()), t.toId(), names.get(t.toId()),
                        t.amount()))
                .toList();
    }

    private static List<CategoryTotal> categoryTotals(List<Expense> expenses, BigDecimal total) {
        Map<Category, BigDecimal> byCategory = expenses.stream().collect(Collectors.groupingBy(
                Expense::getCategory, () -> new EnumMap<>(Category.class),
                Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));

        return byCategory.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .map(entry -> new CategoryTotal(entry.getKey().getLabel(), entry.getKey().getColor(),
                        entry.getValue(), percentOf(entry.getValue(), total)))
                .toList();
    }

    private static ExpenseRow toRow(Expense expense) {
        List<ExpenseRow.Share> shares = expense.shares().entrySet().stream()
                .map(entry -> new ExpenseRow.Share(entry.getKey().getName(), entry.getValue()))
                .toList();
        return new ExpenseRow(expense.getId(), expense.getDescription(), expense.getAmount(),
                expense.getCategory(), expense.getDate(), expense.getPaidBy().getName(),
                expense.getSplitMethod(), shares);
    }

    private static RepaymentRow toRepaymentRow(Repayment repayment) {
        return new RepaymentRow(repayment.getId(), repayment.getFrom().getName(), repayment.getTo().getName(),
                repayment.getAmount(), repayment.getDate(), repayment.getNote());
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    private static BigDecimal average(BigDecimal total, int people) {
        return people == 0 ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(people), 2, RoundingMode.HALF_UP);
    }

    private static int percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).intValue();
    }
}
