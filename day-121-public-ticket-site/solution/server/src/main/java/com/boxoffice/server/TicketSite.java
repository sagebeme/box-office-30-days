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

    TicketSite(Catalogue catalogue, Orders orders) {
        this.catalogue = catalogue;
        this.orders = orders;
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
                if (!fromHtmx) {
                    // Post, then redirect, then get: reloading the order page can't place the order twice.
                    yield "redirect:/orders/" + done.order().reference();
                }
                model.addAttribute("order", done.order());
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
            model.addAttribute("order", order);
            return "order";
        }).orElseGet(() -> notFound(response, model, "No order " + reference));
    }

    private static String notFound(HttpServletResponse response, Model model, String message) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("message", message);
        return "not-found";
    }
}
