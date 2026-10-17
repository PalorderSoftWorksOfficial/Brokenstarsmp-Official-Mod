package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicContextTest {
    @Test
    void selectorMatchesHierarchyAndTags() {
        MechanicContext selector = MechanicContext.builder()
                .scope(MechanicScope.PERMISSION)
                .permission("staff")
                .tag("nether")
                .build();
        MechanicContext context = MechanicContext.builder()
                .dimension("minecraft:the_nether")
                .permission("staff")
                .tag("nether")
                .build();
        assertTrue(selector.matches(context));
        assertTrue(context.specificity() > MechanicContext.global().specificity());
    }
}
