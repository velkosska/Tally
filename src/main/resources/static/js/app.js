/*
 * Small progressive enhancements. Every page works without JavaScript;
 * this only adds confirmations, copy buttons, the split preview, the chart and toast dismissal.
 */
(function () {
    "use strict";

    // Ask before destructive actions: <form data-confirm="Delete this?">
    document.addEventListener("submit", function (event) {
        var message = event.target.getAttribute("data-confirm");
        if (message && !window.confirm(message)) {
            event.preventDefault();
        }
    });

    // Toasts fade out after a few seconds.
    document.querySelectorAll(".toast").forEach(function (toast) {
        setTimeout(function () {
            toast.classList.add("is-leaving");
            setTimeout(function () { toast.remove(); }, 400);
        }, 4500);
    });

    // Copy buttons: <button data-copy="#input-id">. Without JavaScript the link can still be selected by hand.
    document.querySelectorAll("[data-copy]").forEach(function (button) {
        var input = document.querySelector(button.dataset.copy);
        var status = button.closest("div").parentElement.querySelector("[data-copy-status]");
        button.addEventListener("click", function () {
            input.select();
            var done = function () {
                button.textContent = "Copied";
                if (status) { status.textContent = "Copied. Paste it in your group chat."; }
            };
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(input.value).then(done, function () { document.execCommand("copy"); done(); });
            } else {
                document.execCommand("copy");
                done();
            }
        });
    });

    // Expense form: "Everyone / No one" shortcuts, uneven split inputs and a live preview of who pays what.
    var form = document.querySelector("[data-expense-form]");
    if (form) {
        var amountInput = form.querySelector("[data-amount]");
        var boxes = Array.prototype.slice.call(form.querySelectorAll("[data-participant]"));
        var methods = Array.prototype.slice.call(form.querySelectorAll("[data-split-method]"));
        var preview = form.querySelector("[data-preview]");
        var hint = form.querySelector("[data-weight-hint]");
        var money = new Intl.NumberFormat("en-GB", { style: "currency", currency: form.dataset.currency });

        var weightFor = function (box) {
            return box.closest("li").querySelector("[data-weight]");
        };
        var shareFor = function (box) {
            return box.closest("li").querySelector("[data-share]");
        };
        var method = function () {
            var checked = methods.filter(function (m) { return m.checked; })[0];
            return checked ? checked.value : "EQUAL";
        };

        // Same rule as the server: whole cents, leftovers to the largest remainders, lowest id first.
        var allocate = function (totalCents, people) {
            var weightSum = people.reduce(function (sum, p) { return sum + p.weight; }, 0);
            var assigned = 0;
            people.forEach(function (p) {
                var exact = totalCents * p.weight / weightSum;
                p.cents = Math.floor(exact);
                p.remainder = exact - p.cents;
                assigned += p.cents;
            });
            people.slice().sort(function (a, b) { return b.remainder - a.remainder || a.id - b.id; })
                .slice(0, totalCents - assigned)
                .forEach(function (p) { p.cents += 1; });
        };

        var updatePreview = function () {
            var current = method();
            var uneven = current !== "EQUAL";
            var unit = methods.filter(function (m) { return m.checked; })[0];
            hint.hidden = !uneven;
            boxes.forEach(function (box) {
                var input = weightFor(box);
                input.closest("[data-weight-wrap]").hidden = !uneven;
                input.disabled = !box.checked;
                input.closest("li").querySelector("[data-unit]").textContent = unit ? unit.dataset.unit : "";
                shareFor(box).textContent = "";
            });

            var amount = parseFloat(amountInput.value);
            var people = boxes.filter(function (b) { return b.checked; }).map(function (b) {
                return { id: Number(b.value), box: b, weight: uneven ? parseFloat(weightFor(b).value) || 0 : 1 };
            });
            if (!(amount > 0) || people.length === 0) {
                preview.hidden = true;
                return;
            }
            var totalCents = Math.round(amount * 100);
            preview.hidden = false;
            preview.classList.remove("split-preview--off");

            if (current === "EXACT") {
                var given = people.reduce(function (sum, p) { return sum + Math.round(p.weight * 100); }, 0);
                var diff = totalCents - given;
                preview.textContent = diff === 0 ? "The amounts add up to the total."
                    : diff > 0 ? money.format(diff / 100) + " still to assign."
                    : money.format(-diff / 100) + " more than the total.";
                preview.classList.toggle("split-preview--off", diff !== 0);
                return;
            }
            if (current === "PERCENT") {
                var percent = Math.round(people.reduce(function (sum, p) { return sum + p.weight; }, 0) * 100) / 100;
                if (percent !== 100) {
                    preview.textContent = "The percentages add up to " + percent + "%. They need to add up to 100%.";
                    preview.classList.add("split-preview--off");
                    return;
                }
            }
            if (people.some(function (p) { return !(p.weight > 0); })) {
                preview.textContent = "Fill in a number for everyone you ticked.";
                preview.classList.add("split-preview--off");
                return;
            }
            allocate(totalCents, people);
            people.forEach(function (p) { shareFor(p.box).textContent = money.format(p.cents / 100); });
            if (current === "EQUAL") {
                preview.textContent = people.length === 1
                    ? "One person covers the full " + money.format(amount) + "."
                    : "Each of the " + people.length + " people pays about " + money.format(amount / people.length) + ".";
            } else {
                preview.textContent = "Shares shown next to each name.";
            }
        };

        form.querySelectorAll("[data-select]").forEach(function (button) {
            button.addEventListener("click", function () {
                var checkAll = button.dataset.select === "all";
                boxes.forEach(function (b) { b.checked = checkAll; });
                updatePreview();
            });
        });
        form.addEventListener("input", updatePreview);
        form.addEventListener("change", updatePreview);
        updatePreview();
    }

    // Category doughnut. Chart.js loads after this file, so wait for the page to finish.
    window.addEventListener("load", function () {
        var canvas = document.getElementById("category-chart");
        var data = window.tallyCategories;
        if (!canvas || !window.Chart || !data || data.length === 0) {
            return;
        }
        new window.Chart(canvas, {
            type: "doughnut",
            data: {
                labels: data.map(function (c) { return c.label; }),
                datasets: [{
                    data: data.map(function (c) { return Number(c.amount); }),
                    backgroundColor: data.map(function (c) { return c.color; }),
                    borderColor: "#edf0ea",
                    borderWidth: 3,
                    hoverOffset: 6
                }]
            },
            options: {
                cutout: "62%",
                animation: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? false : { duration: 600 },
                plugins: { legend: { display: false } }
            }
        });
    });
})();
