package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.OperatorTarget;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.TargetConflictValidator;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HarnessRouterTest {
    private static final String DOMAIN = HarnessProfileRegistry.DEFAULT_DOMAIN;

    @Test
    void registryHasExactlyFourApprovedStaticProfilesAndBaselineAlwaysExists() {
        HarnessProfileRegistry registry = HarnessProfileRegistry.defaults();

        assertEquals(4, registry.listApproved().size());
        assertEquals("baseline", registry.defaultProfile().profileId());
        assertTrue(registry.defaultProfile().approved());
        assertNull(registry.defaultProfile().pipeline());
        assertNull(registry.defaultProfile().capabilityTag());
        assertTrue(registry.findApproved("prompt-profile").isPresent());
        assertTrue(registry.findApproved("history-profile").isPresent());
        assertTrue(registry.findApproved("tool-profile").isPresent());
        assertTrue(registry.findApproved("missing").isEmpty());
    }

    @Test
    void fixedRouteTableSelectsOnlyApprovedSingleOperatorProfiles() {
        HarnessRouter router = HarnessRouter.defaults();
        assertProfile(router, CapabilityTag.PROMPT_ONLY, "prompt-profile", Operator.CONCISE_SYSTEM_PROMPT);
        assertProfile(router, CapabilityTag.HISTORY, "history-profile", Operator.ADAPTIVE_MEMORY_WINDOW);
        assertProfile(router, CapabilityTag.TOOL, "tool-profile", Operator.TOOL_GUIDANCE_SYNTHESIS);

        for (CapabilityTag unsupported : List.of(CapabilityTag.RAG, CapabilityTag.COMPOSITE)) {
            HarnessRouteDecision decision = router.route(context(unsupported));
            assertEquals("baseline", decision.profile().profileId());
            assertTrue(decision.fallback());
            assertEquals("NO_APPROVED_ROUTE", decision.reason());
        }
    }

    @Test
    void unknownTagAndDomainReturnBaselineWithStableReasonAndFingerprint() {
        HarnessRouter router = HarnessRouter.defaults();
        HarnessRouteDecision first = router.route(new HarnessRoutingContext("r1", DOMAIN, null));
        HarnessRouteDecision second = router.route(new HarnessRoutingContext("r2", DOMAIN, null));
        assertEquals("baseline", first.profile().profileId());
        assertTrue(first.fallback());
        assertEquals("UNKNOWN_CAPABILITY", first.reason());
        assertEquals(first.routeFingerprint(), second.routeFingerprint());
        assertEquals(64, first.routeFingerprint().length());

        HarnessRouteDecision otherDomain = router.route(new HarnessRoutingContext("r3", "OTHER", CapabilityTag.TOOL));
        assertEquals("baseline", otherDomain.profile().profileId());
        assertTrue(otherDomain.fallback());
        assertEquals("DOMAIN_MISMATCH", otherDomain.reason());
        assertNotEquals(first.routeFingerprint(), otherDomain.routeFingerprint());
    }

    @Test
    void unapprovedUnknownAndMismatchedProfilesNeverRoute() {
        HarnessProfile baseline = HarnessProfileRegistry.defaults().defaultProfile();
        HarnessProfile unapproved = profile("unapproved", DOMAIN, CapabilityTag.TOOL, false);
        HarnessProfileRegistry registry = new HarnessProfileRegistry(List.of(baseline, unapproved));
        assertTrue(registry.findApproved("unapproved").isEmpty());
        assertEquals(1, registry.listApproved().size());

        HarnessRouteDecision denied = new HarnessRouter(registry, Map.of(CapabilityTag.TOOL, "unapproved"))
                .route(context(CapabilityTag.TOOL));
        assertEquals("baseline", denied.profile().profileId());
        assertTrue(denied.fallback());
        assertEquals("PROFILE_NOT_APPROVED", denied.reason());

        HarnessRouteDecision unknown = new HarnessRouter(registry, Map.of(CapabilityTag.TOOL, "absent"))
                .route(context(CapabilityTag.TOOL));
        assertEquals("UNKNOWN_PROFILE", unknown.reason());
        assertTrue(unknown.fallback());

        HarnessProfile wrongDomain = profile("wrong-domain", "OTHER", CapabilityTag.TOOL, true);
        HarnessRouteDecision mismatch = new HarnessRouter(
                new HarnessProfileRegistry(List.of(baseline, wrongDomain)),
                Map.of(CapabilityTag.TOOL, "wrong-domain")).route(context(CapabilityTag.TOOL));
        assertEquals("PROFILE_MISMATCH", mismatch.reason());
        assertTrue(mismatch.fallback());
    }

    @Test
    void profileIdentityIsDeterministicAndDuplicateRegistrationIsRejected() {
        HarnessProfile baseline = HarnessProfileRegistry.defaults().defaultProfile();
        HarnessProfile first = profile("tool-a", DOMAIN, CapabilityTag.TOOL, true);
        HarnessProfile same = profile("tool-a", DOMAIN, CapabilityTag.TOOL, true);
        HarnessProfile differentDomain = profile("tool-a", "OTHER", CapabilityTag.TOOL, true);
        assertEquals(first.fingerprint(), same.fingerprint());
        assertNotEquals(first.fingerprint(), differentDomain.fingerprint());
        assertEquals(64, first.fingerprint().length());
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessProfileRegistry(List.of(baseline, first, same)));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessProfileRegistry(List.of(first)));
    }

    @Test
    void stage16PipelineStillRejectsRepeatedTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> new TargetConflictValidator().validateTargets(
                        List.of(OperatorTarget.SYSTEM_PROMPT, OperatorTarget.SYSTEM_PROMPT)));
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedOperatorPipeline(
                        List.of(Operator.CONCISE_SYSTEM_PROMPT, Operator.CONCISE_SYSTEM_PROMPT)));
    }

    @Test
    void requestClassificationUsesObservableCapabilitiesNotJsonOrAutoKnowledgeTools() {
        ToolCallbackResolver resolver = mock(ToolCallbackResolver.class);
        when(resolver.resolve("auditTool")).thenReturn(mock(ToolCallback.class));
        when(resolver.resolve("knowledgeQuery")).thenReturn(mock(ToolCallback.class));
        ChatRequest base = ChatRequest.newBuilder().setRequestId("route")
                .setInputParams("{\"capabilityTag\":\"TOOL\"}").build();
        assertEquals(CapabilityTag.PROMPT_ONLY, HarnessRoutingContext.fromRequest(base, resolver).capabilityTag());

        ChatRequest rag = base.toBuilder().addRagContexts(RAGContext.newBuilder()
                .setKnowledgeId("k1").setPreRetrievedContent("retrieved body").build())
                .addToolMethodIds("knowledgeQuery").build();
        assertEquals(CapabilityTag.RAG, HarnessRoutingContext.fromRequest(rag, resolver).capabilityTag());
        assertEquals(CapabilityTag.TOOL, HarnessRoutingContext.fromRequest(
                base.toBuilder().addToolMethodIds("auditTool").build(), resolver).capabilityTag());
        assertEquals(CapabilityTag.PROMPT_ONLY, HarnessRoutingContext.fromRequest(
                base.toBuilder().addToolMethodIds("missingTool").build(), resolver).capabilityTag());
        assertEquals(CapabilityTag.COMPOSITE, HarnessRoutingContext.fromRequest(
                rag.toBuilder().addToolMethodIds("auditTool").build(), resolver).capabilityTag());
        assertEquals(CapabilityTag.HISTORY, HarnessRoutingContext.fromRequest(
                base.toBuilder().addHistory(ChatMessage.newBuilder().setRole("user")
                        .setContent("prior message").build()).build(), resolver).capabilityTag());
        assertEquals(DOMAIN, HarnessRoutingContext.fromRequest(base, resolver).domain());
    }

    private static void assertProfile(HarnessRouter router, CapabilityTag tag, String expectedId,
                                      Operator expectedOperator) {
        HarnessRouteDecision decision = router.route(context(tag));
        assertEquals(expectedId, decision.profile().profileId());
        assertEquals(List.of(expectedOperator), decision.profile().pipeline().operators());
        assertEquals("MATCHED_CAPABILITY", decision.reason());
        assertFalse(decision.fallback());
        assertEquals(64, decision.routeFingerprint().length());
    }

    private static HarnessRoutingContext context(CapabilityTag tag) {
        return new HarnessRoutingContext("route", DOMAIN, tag);
    }

    private static HarnessProfile profile(String id, String domain, CapabilityTag tag, boolean approved) {
        return new HarnessProfile(id, "1", domain, tag,
                new BoundedOperatorPipeline(List.of(Operator.TOOL_GUIDANCE_SYNTHESIS)), approved, "TEST_WHITELIST");
    }
}
