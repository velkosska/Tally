package com.tally.config;

import com.tally.account.AppUser;
import com.tally.account.AppUserRepository;
import com.tally.domain.Category;
import com.tally.domain.Expense.ExpenseDetails;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.settlement.SplitMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates two demo accounts and two example groups so the app has something to show on first run.
 * Enabled with {@code tally.demo-data.enabled=true} (on by default in the dev profile only).
 *
 * <ul>
 *   <li>{@value #ANA_EMAIL} is in both groups.</li>
 *   <li>{@value #BEN_EMAIL} is only in "Lisbon weekend", which shows that people only see their own groups.</li>
 * </ul>
 * Both use the password {@value #PASSWORD}. Never enable this on a public server.
 */
@Component
@ConditionalOnProperty(name = "tally.demo-data.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    static final String ANA_EMAIL = "ana@example.com";
    static final String BEN_EMAIL = "ben@example.com";
    static final String PASSWORD = "tally-demo";

    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DemoDataLoader(ExpenseGroupRepository groups, AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.groups = groups;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(ANA_EMAIL)) {
            return;
        }
        AppUser ana = createUser(ANA_EMAIL, "Ana");
        AppUser ben = createUser(BEN_EMAIL, "Ben");
        createFlat(ana);
        createTrip(ana, ben);
        log.info("Loaded demo data: 2 groups. Log in as {} or {} with password '{}'", ANA_EMAIL, BEN_EMAIL, PASSWORD);
    }

    private AppUser createUser(String email, String name) {
        return users.save(new AppUser(email, name, passwordEncoder.encode(PASSWORD)));
    }

    private void createTrip(AppUser anaUser, AppUser benUser) {
        ExpenseGroup trip = groups.saveAndFlush(
                new ExpenseGroup("Lisbon weekend", "Four friends, three days, too many pastéis de nata", "EUR"));
        Member ana = trip.addMember("Ana", anaUser);
        Member ben = trip.addMember("Ben", benUser);
        Member carla = trip.addMember("Carla");
        Member dani = trip.addMember("Dani");
        groups.flush();

        LocalDate start = LocalDate.now().minusDays(12);
        List<Member> all = List.of(ana, ben, carla, dani);
        // Carla arrived a night later, so the apartment is split by nights stayed.
        Map<Member, BigDecimal> nights = new LinkedHashMap<>();
        nights.put(ana, new BigDecimal("3"));
        nights.put(ben, new BigDecimal("3"));
        nights.put(carla, new BigDecimal("2"));
        nights.put(dani, new BigDecimal("3"));
        trip.addExpense(new ExpenseDetails("Apartment in Alfama", new BigDecimal("384.00"), Category.ACCOMMODATION,
                start, ana, SplitMethod.SHARES, nights));
        add(trip, "Flights to Lisbon", "236.80", Category.TRANSPORT, start, ben, all);
        add(trip, "Seafood dinner at the harbour", "142.50", Category.FOOD, start.plusDays(1), carla, all);
        add(trip, "Tram 28 tickets", "12.00", Category.TRANSPORT, start.plusDays(1), dani, List.of(ana, dani, ben));
        add(trip, "Fado show", "90.00", Category.ENTERTAINMENT, start.plusDays(1), ana, List.of(ana, carla, dani));
        add(trip, "Pastéis de nata (again)", "15.60", Category.FOOD, start.plusDays(2), ben, all);
        add(trip, "Supermarket breakfast run", "31.45", Category.GROCERIES, start.plusDays(2), dani, all);
        trip.recordRepayment(dani.getId(), ana.getId(), new BigDecimal("50.00"), start.plusDays(4),
                "Bank transfer", null);
    }

    private void createFlat(AppUser anaUser) {
        ExpenseGroup flat = groups.saveAndFlush(new ExpenseGroup("Flat 3B", "Shared bills and groceries", "EUR"));
        Member ana = flat.addMember("Ana", anaUser);
        Member leo = flat.addMember("Leo");
        Member sam = flat.addMember("Sam");
        groups.flush();

        LocalDate today = LocalDate.now();
        List<Member> all = List.of(ana, leo, sam);
        add(flat, "Electricity bill", "87.30", Category.UTILITIES, today.minusDays(20), ana, all);
        add(flat, "Internet", "39.99", Category.UTILITIES, today.minusDays(18), leo, all);
        add(flat, "Weekly groceries", "64.20", Category.GROCERIES, today.minusDays(3), sam, all);
    }

    private static void add(ExpenseGroup group, String description, String amount, Category category,
                            LocalDate date, Member paidBy, List<Member> participants) {
        Map<Member, BigDecimal> equally = new LinkedHashMap<>();
        participants.forEach(member -> equally.put(member, null));
        group.addExpense(new ExpenseDetails(description, new BigDecimal(amount), category,
                date, paidBy, SplitMethod.EQUAL, equally));
    }
}
