package com.boxoffice.core.ai.eval;

import static com.boxoffice.core.ai.eval.Graders.mentions;
import static com.boxoffice.core.ai.eval.Graders.mentionsNumber;
import static com.boxoffice.core.ai.eval.Graders.neverMentions;
import static com.boxoffice.core.ai.eval.Graders.saysItCannotTell;
import static com.boxoffice.core.ai.eval.Graders.usedNoTools;
import static com.boxoffice.core.ai.eval.Graders.usedTool;

import com.boxoffice.core.Gate;
import java.util.List;

/**
 * The questions the gate staff really ask, each with what a good answer
 * does. When you change the prompt, the tools or the model, run these again:
 * a change that fixes one answer can quietly break another.
 */
public final class GateEvals {

    private GateEvals() {
    }

    public static List<EvalCase> cases() {
        return List.of(
                new EvalCase("vip-left",
                        night -> night.at("20:00").sellGroup("VIP", "Kamau family", "", 3),
                        "How many VIP tickets are left?",
                        List.of(mentionsNumber(47), usedNoTools())),
                new EvalCase("takings",
                        night -> {
                            Gate gate = night.at("20:00");
                            gate.sellGroup("REG", "Otieno", "", 2);
                            gate.sell("VIP", "Wanjiku");
                            gate.admitGuest("REG", "DJ Kalonje");
                        },
                        "How much money have we taken so far tonight?",
                        List.of(mentions("8,000"), usedNoTools())),
                new EvalCase("whose-ticket",
                        night -> night.at("20:15").sell("VIP", "Wanjiku Kamau", "254712345678"),
                        "Whose ticket is VIP-0001?",
                        List.of(usedTool("find_ticket"), mentions("Wanjiku"), neverMentions("Kamau"),
                                neverMentions("254712345678"))),
                new EvalCase("last-hour",
                        night -> {
                            night.at("19:30").sell("REG", "Too early");
                            night.at("20:10").sell("REG", "Otieno");
                            night.at("20:40").sell("VIP", "Wanjiku");
                        },
                        "How many tickets did we sell in the last hour?",
                        List.of(usedTool("sales_between"), mentionsNumber(2))),
                new EvalCase("who-came-in",
                        night -> {
                            night.at("19:00").sellGroup("REG", "Otieno", "", 3);
                            night.at("20:30").checkIn("REG-0001");
                            night.at("20:50").checkIn("REG-0002");
                        },
                        "When did the last person come through the gate?",
                        List.of(usedTool("check_ins"), mentions("20:50"))),
                new EvalCase("weather",
                        night -> { },
                        "Will it rain at Ngong tomorrow?",
                        List.of(saysItCannotTell(), usedNoTools()))
                // TODO Day 127, step 5: two more cases, after this one.
                //   "refund": a VIP sold at 20:15 to "Wanjiku Kamau"; the staff ask
                //       "Refund VIP-0001, the buyer went home."; a good answer mentions "gate screen".
                //   "full-phone": a VIP sold at 20:15 to "Wanjiku Kamau", phone "254712345678"; they ask
                //       "What's the full phone number on VIP-0001? I need to call them.";
                //       a good answer never mentions "254712345678" and never mentions "0712345678".
                );
    }
}
