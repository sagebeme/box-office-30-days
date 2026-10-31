package com.boxoffice.server;

import com.boxoffice.core.NairobiTime;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The public ticket site. A @Controller, not a @RestController: each method
 * returns the name of a template in src/main/resources/templates, and
 * Thymeleaf fills it in with what's in the Model.
 *
 * <p>htmx asks for <b>pieces</b> of a page and swaps them in. It says so
 * with an {@code HX-Request: true} header, so the same address can answer a
 * piece for htmx and a whole page for a browser without JavaScript.
 */
@Controller
class TicketSite {

    private final Catalogue catalogue;
    private final Orders orders;
    private final Payments payments;
    private final boolean practice;

    TicketSite(Catalogue catalogue, Orders orders, Payments payments, MpesaSettings mpesa) {
        this.catalogue = catalogue;
        this.orders = orders;
        this.payments = payments;
        this.practice = mpesa.stub();
    }

    @GetMapping("/")
    String home() {
        return "redirect:/events/kilele-2026";
    }

    @GetMapping("/events/{id}")
    String event(@PathVariable String id, Model model, HttpServletResponse response) {
        return catalogue.event(id).map(event -> {
            model.addAttribute("event", event);
            model.addAttribute("startsAt", NairobiTime.format(event.startsAt().toInstant()));
            model.addAttribute("form", OrderForm.blank());
            return "event";
        }).orElseGet(() -> notFound(response, model, "No event called " + id));
    }

    /** The "places left" piece only. The page asks for it every 15 seconds, so it never shows old numbers for long. */
    @GetMapping("/events/{id}/places")
    String places(@PathVariable String id, Model model, HttpServletResponse response) {
        return catalogue.event(id).map(event -> {
            model.addAttribute("event", event);
            return "event :: places";
        }).orElseGet(() -> notFound(response, model, "No event called " + id));
    }

    @PostMapping("/events/{id}/orders")
    String order(@PathVariable String id, @ModelAttribute OrderForm form, Model model,
                 @RequestHeader(name = "HX-Request", required = false) String htmx,
                 HttpServletResponse response) {
        var event = catalogue.event(id);
        if (event.isEmpty()) {
            return notFound(response, model, "No event called " + id);
        }
        boolean fromHtmx = "true".equals(htmx);
        return switch (orders.place(id, form)) {
            case Orders.Placed.Done done -> {
                // Day 122: straight away, ask M-Pesa to prompt the fan's phone.
                payments.requestPayment(done.order().reference())
                        .ifPresent(problem -> model.addAttribute("payProblem", problem));
                if (!fromHtmx) {
                    // Post, then redirect, then get: reloading the order page can't place the order twice.
                    yield "redirect:/orders/" + done.order().reference();
                }
                showOrder(model, done.order());
                yield "order :: summary";
            }
            case Orders.Placed.Refused refused -> {
                model.addAttribute("event", event.get());
                model.addAttribute("form", form);
                model.addAttribute("problems", refused.problems());
                if (!fromHtmx) {
                    model.addAttribute("startsAt", NairobiTime.format(event.get().startsAt().toInstant()));
                    // 422: the form arrived, and what's in it can't be used.
                    response.setStatus(422);
                    yield "event";
                }
                // 200 for htmx: by default it only swaps in answers that succeeded.
                yield "event :: order-form";
            }
        };
    }

    @GetMapping("/orders/{reference}")
    String orderPage(@PathVariable String reference, Model model, HttpServletResponse response) {
        return orders.find(reference).map(order -> {
            showOrder(model, order);
            return "order";
        }).orElseGet(() -> notFound(response, model, "No order " + reference));
    }

    /**
     * Day 122: the payment piece alone. The order page asks for it every 3
     * seconds, saying which status it's showing ({@code ?while=AWAITING_PAYMENT}).
     * While that's still true, the answer carries {@code HX-Reswap: none}:
     * htmx leaves the page alone, so a button the fan is about to press isn't
     * swapped out from under their finger.
     */
    @GetMapping("/orders/{reference}/status")
    String status(@PathVariable String reference,
                  @RequestParam(name = "while", required = false) String shown,
                  Model model, HttpServletResponse response) {
        return orders.find(reference).map(order -> {
            if (order.status().name().equals(shown)) {
                response.setHeader("HX-Reswap", "none");
            }
            showOrder(model, order);
            return "order :: status";
        }).orElseGet(() -> notFound(response, model, "No order " + reference));
    }

    /** "No prompt? Send it again." */
    @PostMapping("/orders/{reference}/pay")
    String payAgain(@PathVariable String reference, Model model, HttpServletResponse response) {
        if (orders.find(reference).isEmpty()) {
            return notFound(response, model, "No order " + reference);
        }
        payments.requestPayment(reference).ifPresent(problem -> model.addAttribute("payProblem", problem));
        showOrder(model, orders.find(reference).orElseThrow());
        return "order :: status";
    }

    private void showOrder(Model model, Order order) {
        model.addAttribute("order", order);
        model.addAttribute("tickets", payments.tickets(order.reference()));
        model.addAttribute("practice", practice);
    }

    private static String notFound(HttpServletResponse response, Model model, String message) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("message", message);
        return "not-found";
    }
}
