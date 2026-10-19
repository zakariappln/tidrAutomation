package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.fixtures.AdminHttp.DomainList;
import java.util.regex.Pattern;

/**
 * List-agnostic bodies of the per-list phrases ("I add a blocked/allowed domain that is N
 * characters long", ...). Not glue itself (no annotations): each list's step class holds one
 * instance bound to its list, so every phrase still has exactly one Java method.
 */
final class DomainListSteps {

    private final UiScenarioContext ctx;
    private final DomainLists lists;
    private final DomainList list;

    DomainListSteps(UiScenarioContext ctx, DomainLists lists, DomainList list) {
        this.ctx = ctx;
        this.lists = lists;
        this.list = list;
    }

    /**
     * A domain-shaped name of exactly {@code length} characters: 63-character labels (the DNS label
     * maximum) joined by dots, so only its overall length can make it invalid.
     */
    void addDomainOfLength(int length) {
        lists.addThroughUi(list, domainOfLength(length));
    }

    void typeValueOfLength(int length) {
        lists.page(list).typeDomain(domainOfLength(length));
    }

    void fieldHoldsAtMost(int max) {
        assertThat(lists.page(list).domainInput())
                .hasValue(Pattern.compile("^.{1,%d}$".formatted(max)));
    }

    /**
     * Creates {@code count + 1} domains over HTTP (all cleaned up), so more than {@code count}
     * exist.
     */
    void createMoreThan(int count) {
        String prefix = list == DomainList.BLOCKED ? "qa-bpage-" : "qa-apage-";
        for (int i = 0; i <= count; i++) {
            lists.createOverHttp(list, ctx.resolve(prefix + "{uniqueString}-" + i + ".example"));
        }
    }

    private String domainOfLength(int length) {
        String unique = ctx.resolve("{uniqueString}").replaceAll("[^a-z0-9]", "");
        StringBuilder name = new StringBuilder();
        while (name.length() < length) {
            if (!name.isEmpty()) {
                name.append('.');
            }
            int labelLength = Math.min(63, length - name.length());
            String label = (unique + "a".repeat(63)).substring(0, labelLength);
            name.append(label);
        }
        return name.toString();
    }
}
