/*
 * Small progressive enhancements. Every page works without JavaScript;
 * this only adds confirmations, the split preview, the chart and toast dismissal.
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

    // Expense form: "Everyone / No one" shortcuts and a live "each pays" preview.
    var form = document.querySelector("[data-expense-form]");
    if (form) {
        var amountInput = form.querySelector("[data-amount]");
        var boxes = Array.prototype.slice.call(form.querySelectorAll("[data-participant]"));
        var preview = form.querySelector("[data-preview]");
        var money = new Intl.NumberFormat("en-GB", { style: "currency", currency: form.dataset.currency });

        var updatePreview = function () {
            var amount = parseFloat(amountInput.value);
            var people = boxes.filter(function (b) { return b.checked; }).length;
            if (!(amount > 0) || people === 0) {
                preview.hidden = true;
                return;
            }
            preview.hidden = false;
            preview.textContent = people === 1
                ? "One person covers the full " + money.format(amount) + "."
                : "Each of the " + people + " people pays about " + money.format(amount / people) + ".";
        };

        form.querySelectorAll("[data-select]").forEach(function (button) {
            button.addEventListener("click", function () {
                var checkAll = button.dataset.select === "all";
                boxes.forEach(function (b) { b.checked = checkAll; });
                updatePreview();
            });
        });
        amountInput.addEventListener("input", updatePreview);
        boxes.forEach(function (b) { b.addEventListener("change", updatePreview); });
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
