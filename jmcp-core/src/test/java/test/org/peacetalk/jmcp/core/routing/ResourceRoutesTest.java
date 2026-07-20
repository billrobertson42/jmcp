/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.org.peacetalk.jmcp.core.routing;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.routing.ResourceRoutes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ResourceRoutes} in isolation -- no JDBC involved. Covers matching,
 * guards, the builder's fail-fast checks (guard typo, exact duplicate), shadow detection, and
 * the diagnostics data the startup log lines are built from.
 */
class ResourceRoutesTest {

    /** Minimal stand-in Resource so factories don't need a real implementation. */
    private record StubResource(String uri) implements Resource {
        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public String getName() {
            return "stub";
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getMimeType() {
            return "text/plain";
        }

        @Override
        public String read() {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Basic matching
    // ------------------------------------------------------------------

    @Test
    void resolveMatchesLiteralRoute() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("db://connections"))
                .build();

        Resource r = routes.resolve("db://connections");

        assertInstanceOf(StubResource.class, r);
        assertEquals("db://connections", r.getUri());
    }

    @Test
    void resolveBindsParameterValue() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("bound:" + p.get("id")))
                .build();

        Resource r = routes.resolve("db://connection/prod");

        assertEquals("bound:prod", r.getUri(), "the :id segment must be bound to the factory's Params");
    }

    @Test
    void resolveBindsMultipleParameters() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id/schema/:schema",
                        p -> new StubResource(p.get("id") + "/" + p.get("schema")))
                .build();

        Resource r = routes.resolve("db://connection/prod/schema/PUBLIC");

        assertEquals("prod/PUBLIC", r.getUri());
    }

    @Test
    void resolveReturnsNullForWrongScheme() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("jdbc://connections"));
    }

    @Test
    void resolveReturnsNullForNullUri() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve(null));
    }

    @Test
    void resolveReturnsNullForEmptyPath() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("db://"));
    }

    @Test
    void resolveReturnsNullForEmptySegment() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id/schemas", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("db://connection//schemas"), "an empty segment must not match");
    }

    @Test
    void resolveReturnsNullForTrailingSlash() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("db://connection/prod/"), "a trailing slash produces a trailing empty segment");
    }

    @Test
    void resolveReturnsNullForNoMatchingRoute() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("db://connection/prod/schema/PUBLIC/table/USERS/extra"));
    }

    @Test
    void resolveIsCaseSensitiveOnLiterals() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("db://CONNECTION/prod"));
    }

    @Test
    void resolveTriesRoutesInRegistrationOrderFirstMatchWins() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("generic:" + p.get("id")))
                .route("connection/special", p -> new StubResource("literal-shadowed"))
                .build();

        // "special" matches the generic :id route registered first, so the literal route
        // registered second is unreachable -- this is exactly the shadow case.
        Resource r = routes.resolve("db://connection/special");

        assertEquals("generic:special", r.getUri());
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    @Test
    void guardPassingAllowsFactoryToRun() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("id", id -> id.equals("prod"))
                .route("connection/:id", p -> new StubResource("ok:" + p.get("id")))
                .build();

        Resource r = routes.resolve("db://connection/prod");

        assertEquals("ok:prod", r.getUri());
    }

    @Test
    void guardFailingBlocksFactoryFromRunningAndReturnsNull() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("id", id -> id.equals("prod"))
                .route("connection/:id", p -> {
                    throw new AssertionError("factory must not run when a guard fails");
                })
                .build();

        assertNull(routes.resolve("db://connection/nope"));
    }

    @Test
    void guardOnlyAppliesToRoutesThatBindThatName() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("id", id -> false) // always fails
                .route("connection/:id", p -> new StubResource("db://connection/" + p.get("id")))
                .route("context", p -> new StubResource("db://context"))
                .build();

        // "context" binds no "id" parameter, so the always-failing guard must not apply to it.
        Resource r = routes.resolve("db://context");
        assertEquals("db://context", r.getUri());

        // Sanity check that the guard is in fact wired up and would block a route that does bind :id.
        assertNull(routes.resolve("db://connection/prod"));
    }

    @Test
    void multipleGuardsOnSameNameAllMustPass() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("id", id -> id.startsWith("p"))
                .guard("id", id -> id.endsWith("d"))
                .route("connection/:id", p -> new StubResource("ok:" + p.get("id")))
                .build();

        assertEquals("ok:prod", routes.resolve("db://connection/prod").getUri());
        assertNull(routes.resolve("db://connection/prox"), "second guard (endsWith d) must also be enforced");
    }

    // ------------------------------------------------------------------
    // Fail-fast builder checks
    // ------------------------------------------------------------------

    @Test
    void buildThrowsWhenGuardNamesUnusedParameter() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("connectionId", id -> true) // typo: no route binds :connectionId, only :id
                .route("connection/:id", p -> new StubResource("x"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, routes::build);
        assertTrue(ex.getMessage().contains("connectionId"), "error must name the offending guard parameter");
    }

    @Test
    void buildSucceedsWhenEveryGuardNameIsUsed() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("id", id -> true)
                .route("connection/:id", p -> new StubResource("x"));

        assertEquals(routes, routes.build(), "build() should return the same, now-locked instance");
    }

    @Test
    void buildThrowsOnExactDuplicateLiteralRoutes() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("first"))
                .route("connections", p -> new StubResource("second"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, routes::build);
        assertTrue(ex.getMessage().contains("connections"));
    }

    @Test
    void buildThrowsOnExactDuplicateParameterizedRoutes() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("first"))
                .route("connection/:other", p -> new StubResource("second"));

        // Same shape (literal "connection" then a single param) even though the param is
        // named differently -- the second route can never be reached either way.
        assertThrows(IllegalStateException.class, routes::build);
    }

    @Test
    void routeThrowsWhenPatternBindsSameParameterTwice() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> routes.route("connection/:id/schema/:id", p -> new StubResource("x")));
        assertTrue(ex.getMessage().contains(":id"));
    }

    @Test
    void routeAndGuardThrowAfterBuild() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connections", p -> new StubResource("x"))
                .build();

        assertThrows(IllegalStateException.class, () -> routes.route("context", p -> new StubResource("y")));
        assertThrows(IllegalStateException.class, () -> routes.guard("id", id -> true));
    }

    // ------------------------------------------------------------------
    // Diagnostics: patterns, parameter usage counts, guard coverage, shadow warnings
    // ------------------------------------------------------------------

    @Test
    void diagnosticsListsPatternsInRegistrationOrder() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("context", p -> new StubResource("x"))
                .route("connections", p -> new StubResource("x"))
                .route("connection/:id", p -> new StubResource("x"))
                .build();

        assertEquals(List.of("context", "connections", "connection/:id"), routes.diagnostics().patterns());
    }

    @Test
    void diagnosticsCountsDistinctParameterUsageAcrossRoutes() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("x"))
                .route("connection/:id/schemas", p -> new StubResource("x"))
                .route("connection/:id/schema/:schema", p -> new StubResource("x"))
                .build();

        Map<String, Integer> counts = routes.diagnostics().paramUsageCounts();

        assertEquals(3, counts.get("id"), ":id appears in all three registered routes");
        assertEquals(1, counts.get("schema"), ":schema appears in exactly one registered route");
        assertEquals(2, counts.size(), "no extra parameter names should be reported");
    }

    @Test
    void diagnosticsReportsGuardCoveragePerRegisteredGuard() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .guard("id", id -> true)
                .route("connection/:id", p -> new StubResource("x"))
                .route("connection/:id/schemas", p -> new StubResource("x"))
                .route("context", p -> new StubResource("x"))
                .build();

        List<ResourceRoutes.GuardCoverage> coverage = routes.diagnostics().guardCoverage();

        assertEquals(1, coverage.size());
        assertEquals("id", coverage.get(0).paramName());
        assertEquals(2, coverage.get(0).routeCount(), "only 2 of the 3 routes bind :id");
    }

    @Test
    void diagnosticsFlagsShadowedRouteWithBothPatternsAndPositions() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("generic"))
                .route("connection/special", p -> new StubResource("specific"))
                .build();

        List<ResourceRoutes.ShadowWarning> warnings = routes.diagnostics().shadowWarnings();

        assertEquals(1, warnings.size());
        ResourceRoutes.ShadowWarning w = warnings.get(0);
        assertEquals("connection/:id", w.earlierPattern());
        assertEquals(1, w.earlierPosition());
        assertEquals("connection/special", w.laterPattern());
        assertEquals(2, w.laterPosition());
    }

    @Test
    void diagnosticsReportsNoShadowWarningsWhenLiteralsPrecedeParams() {
        // The doc's own recommended ordering: literal routes registered before a generic
        // :param route of the same shape they'd otherwise be shadowed by.
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/special", p -> new StubResource("specific"))
                .route("connection/:id", p -> new StubResource("generic"))
                .build();

        assertTrue(routes.diagnostics().shadowWarnings().isEmpty());
        // And the literal route is actually reachable, since it was registered first.
        assertEquals("specific", routes.resolve("db://connection/special").getUri());
        assertEquals("generic", routes.resolve("db://connection/other").getUri());
    }

    @Test
    void diagnosticsReportsNoShadowWarningsForDisjointLiterals() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id/schemas", p -> new StubResource("x"))
                .route("connection/:id/relationships", p -> new StubResource("x"))
                .build();

        assertTrue(routes.diagnostics().shadowWarnings().isEmpty(),
                "different literal tails at the same position must not be reported as shadowing");
    }

    // ------------------------------------------------------------------
    // templates()
    // ------------------------------------------------------------------

    @Test
    void templatesRendersParamsAsRfc6570Braces() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id/schema/:schema/table/:table", p -> new StubResource("x"))
                .build();

        assertEquals(List.of("db://connection/{id}/schema/{schema}/table/{table}"), routes.templates());
    }

    // ------------------------------------------------------------------
    // Percent-decoding of resolved segments (coordinated with PathEncoding.encodeSegment)
    // ------------------------------------------------------------------

    @Test
    void resolveDecodesPercentEncodedSegmentsButNotFormEncodedPlus() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id/schema/:schema", p -> new StubResource(p.get("schema")))
                .build();

        // %2F must decode to '/', and a literal '+' must NOT become a space (that would be
        // application/x-www-form-urlencoded semantics, which this is deliberately not).
        Resource r = routes.resolve("db://connection/prod/schema/weird%2Fname%2Bplus");

        assertEquals("weird/name+plus", r.getUri());
    }

    @Test
    void resolveReturnsNullForMalformedPercentEscape() {
        ResourceRoutes routes = ResourceRoutes.forScheme("db")
                .route("connection/:id", p -> new StubResource("x"))
                .build();

        assertNull(routes.resolve("db://connection/bad%ZZvalue"), "an invalid %-escape must not match, not throw");
        assertNull(routes.resolve("db://connection/trunc%2"), "a truncated %-escape must not match, not throw");
    }
}
