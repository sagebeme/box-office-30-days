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
        Orders.Placed placed = orders.place(id, form);
        // TODO Day 121, step 4: answer htmx with a piece of a page, and a browser with a whole one.
        //   Placed.Done done:
        //     from htmx:   put done.order() in the model as "order", return "order :: summary"
        //     otherwise:   return "redirect:/orders/" + the order's reference
        //   Placed.Refused refused:
        //     put event.get() as "event", form as "form" and refused.problems() as "problems" in the model
        //     from htmx:   return "event :: order-form" (status 200: htmx only swaps in answers that succeeded)
        //     otherwise:   also put "startsAt" (as the event method does), set status 422, return "event"
        // A switch over placed, with a case for each kind, does it neatly.
        throw new UnsupportedOperationException("Not written yet: Day 121, step 4");
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
