package com.tally.config;

import com.tally.domain.Category;
import com.tally.domain.Expense.ExpenseDetails;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fills an empty database with two example groups so the app has something to show on first run.
 * Enabled with {@code tally.demo-data.enabled=true} (on by default in the dev profile only).
 */
@Component
@ConditionalOnProperty(name = "tally.demo-data.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final ExpenseGroupRepository groups;

    public DemoDataLoader(ExpenseGroupRepository groups) {
        this.groups = groups;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (groups.count() > 0) {
            return;
        }
        createFlat();
        createTrip();
        log.info("Loaded demo data: 2 groups");
    }

    private void createTrip() {
        ExpenseGroup trip = groups.saveAndFlush(
                new ExpenseGroup("Lisbon weekend", "Four friends, three days, too many pastéis de nata", "EUR"));
        Member ana = trip.addMember("Ana");
        Member ben = trip.addMember("Ben");
        Member carla = trip.addMember("Carla");
        Member dani = trip.addMember("Dani");
        groups.flush();

        LocalDate start = LocalDate.now().minusDays(12);
        List<Member> all = List.of(ana, ben, carla, dani);
        add(trip, "Apartment in Alfama", "384.00", Category.ACCOMMODATION, start, ana, all);
        add(trip, "Flights to Lisbon", "236.80", Category.TRANSPORT, start, ben, all);
        add(trip, "Seafood dinner at the harbour", "142.50", Category.FOOD, start.plusDays(1), carla, all);
        add(trip, "Tram 28 tickets", "12.00", Category.TRANSPORT, start.plusDays(1), dani, List.of(ana, dani, ben));
        add(trip, "Fado show", "90.00", Category.ENTERTAINMENT, start.plusDays(1), ana, List.of(ana, carla, dani));
        add(trip, "Pastéis de nata (again)", "15.60", Category.FOOD, start.plusDays(2), ben, all);
        add(trip, "Supermarket breakfast run", "31.45", Category.GROCERIES, start.plusDays(2), dani, all);
    }

    private void createFlat() {
        ExpenseGroup flat = groups.saveAndFlush(new ExpenseGroup("Flat 3B", "Shared bills and groceries", "EUR"));
        Member maya = flat.addMember("Maya");
        Member leo = flat.addMember("Leo");
        Member sam = flat.addMember("Sam");
        groups.flush();

        LocalDate today = LocalDate.now();
        List<Member> all = List.of(maya, leo, sam);
        add(flat, "Electricity bill", "87.30", Category.UTILITIES, today.minusDays(20), maya, all);
        add(flat, "Internet", "39.99", Category.UTILITIES, today.minusDays(18), leo, all);
        add(flat, "Weekly groceries", "64.20", Category.GROCERIES, today.minusDays(3), sam, all);
    }

    private static void add(ExpenseGroup group, String description, String amount, Category category,
                            LocalDate date, Member paidBy, List<Member> participants) {
        group.addExpense(new ExpenseDetails(description, new BigDecimal(amount), category,
                date, paidBy, participants));
    }
}
