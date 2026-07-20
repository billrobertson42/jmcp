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

package org.peacetalk.jmcp.core.routing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.core.Resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A small route table mapping {@code scheme://...} resource URIs to {@link Resource}
 * factories.
 *
 * <p>Build one with {@link #forScheme(String)}, register routes and guards, then call
 * {@link #build()} once (typically at startup, after every dependency the factories close
 * over — e.g. a connection manager — is available). {@link #resolve(String)} does the rest:
 *
 * <pre>{@code
 * ResourceRoutes routes = ResourceRoutes.forScheme("db")
 *     .guard("id", this::connectionExists)
 *     .route("connection/:id", p -> new ConnectionResource(p.get("id"), connectionManager))
 *     .build();
 *
 * Resource r = routes.resolve("db://connection/prod");
 * }</pre>
 *
 * <h2>Matching</h2>
 * A pattern is a {@code /}-separated list of segments, each either a literal (matches that
 * exact text) or a {@code :name} parameter (matches any single non-empty segment and binds it).
 * Routes are tried in registration order; the first full match wins. See {@link #resolve} for
 * the exact algorithm.
 *
 * <h2>Fail-fast checks (at {@link #build()})</h2>
 * <ul>
 *   <li>A guard registered for a parameter name that no route binds throws
 *       {@link IllegalStateException} — almost always a typo.</li>
 *   <li>Two routes with identical shape (same segment count, same literal-vs-parameter pattern
 *       at every position) throw {@link IllegalStateException} — the second can never be
 *       reached.</li>
 *   <li>A route pattern that binds the same parameter name twice throws
 *       {@link IllegalArgumentException} at {@link #route} time — the second binding would
 *       silently overwrite the first in {@link Params}.</li>
 * </ul>
 * A route that is merely <em>shadowed</em> (unreachable because an earlier, more general route
 * already matches everything it would match) is not an error — the design doc's own guidance
 * allows registering a general route before specific ones it deliberately shadows would be
 * wrong, but the reverse (general after specific) is sometimes intentional-looking and only
 * gets a {@code WARN} log, not a thrown exception.
 *
 * <p>Not thread-safe to build; safe to call {@link #resolve} concurrently once built.
 */
public final class ResourceRoutes {

    private static final Logger LOG = LogManager.getLogger(ResourceRoutes.class);

    private final String scheme;
    private final List<Route> routes = new ArrayList<>();
    private final List<GuardEntry> guards = new ArrayList<>();
    private boolean built = false;
    private RouteDiagnostics diagnostics;

    private ResourceRoutes(String scheme) {
        this.scheme = scheme;
    }

    /**
     * @param scheme the URI scheme this table resolves, e.g. {@code "db"} (no {@code "://"})
     */
    public static ResourceRoutes forScheme(String scheme) {
        if (scheme == null || scheme.isEmpty()) {
            throw new IllegalArgumentException("scheme must not be null or empty");
        }
        return new ResourceRoutes(scheme);
    }

    /**
     * Registers a route. {@code pattern} is scheme-relative, e.g. {@code "connection/:id/schemas"}
     * (no leading slash, no scheme).
     *
     * @throws IllegalArgumentException if the pattern is empty, has an empty segment, an empty
     *                                  parameter name ({@code ":"}), or binds the same parameter
     *                                  name more than once
     * @throws IllegalStateException    if called after {@link #build()}
     */
    public ResourceRoutes route(String pattern, Function<Params, Resource> factory) {
        ensureNotBuilt();
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        routes.add(new Route(pattern, parsePattern(pattern), factory));
        return this;
    }

    /**
     * Registers a guard: whenever any matched route binds a parameter named {@code paramName},
     * {@code predicate} runs on the bound value before the route's factory is invoked. A guard
     * returning {@code false} makes {@link #resolve} return {@code null} without invoking the
     * factory. Multiple guards may be registered for the same name; all must pass.
     *
     * @throws IllegalStateException if called after {@link #build()}
     */
    public ResourceRoutes guard(String paramName, Predicate<String> predicate) {
        ensureNotBuilt();
        if (paramName == null || paramName.isEmpty()) {
            throw new IllegalArgumentException("paramName must not be null or empty");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate must not be null");
        }
        guards.add(new GuardEntry(paramName, predicate));
        return this;
    }

    /**
     * Finalizes the route table: runs the fail-fast checks, logs startup diagnostics, and locks
     * the table against further {@link #route}/{@link #guard} calls. Idempotent — calling it
     * again is a no-op. {@link #resolve}, {@link #scheme()}, {@link #templates()}, and
     * {@link #diagnostics()} all call this automatically if it hasn't run yet, so calling it
     * explicitly is only needed to fail fast at startup rather than on first use.
     *
     * @throws IllegalStateException if a guard name has no matching route, or if two routes
     *                                have identical shape
     */
    public ResourceRoutes build() {
        if (built) {
            return this;
        }
        validateGuardNames();
        detectExactDuplicates();
        diagnostics = computeDiagnostics();
        logDiagnostics(diagnostics);
        built = true;
        return this;
    }

    /**
     * Resolves a URI to a {@link Resource}, or {@code null} if nothing matches. Order of
     * operations:
     * <ol>
     *   <li>Scheme check — {@code uri} must start with {@code scheme + "://"}.</li>
     *   <li>Split the remainder on {@code /}. An empty path, an empty segment
     *       ({@code connection//schemas}), or a trailing slash yields {@code null}. Each
     *       segment is percent-decoded ({@link PathEncoding#decodeSegment}); a malformed
     *       {@code %} escape yields {@code null} rather than throwing.</li>
     *   <li>The first registered route whose segment count and literal segments match wins.</li>
     *   <li>Every guard registered for a name the matched route bound must pass, or the result
     *       is {@code null} and the factory is never invoked.</li>
     *   <li>The route's factory runs with the bound {@link Params}.</li>
     * </ol>
     */
    public Resource resolve(String uri) {
        ensureBuilt();
        if (uri == null) {
            return null;
        }
        String prefix = scheme + "://";
        if (!uri.startsWith(prefix)) {
            return null;
        }
        String path = uri.substring(prefix.length());
        String[] rawSegments = path.split("/", -1);
        String[] segments = new String[rawSegments.length];
        for (int i = 0; i < rawSegments.length; i++) {
            if (rawSegments[i].isEmpty()) {
                return null;
            }
            try {
                segments[i] = PathEncoding.decodeSegment(rawSegments[i]);
            } catch (IllegalArgumentException malformedEscape) {
                return null;
            }
        }
        for (Route route : routes) {
            if (route.matches(segments)) {
                Params params = route.bind(segments);
                if (!passesGuards(params)) {
                    return null;
                }
                return route.factory().apply(params);
            }
        }
        return null;
    }

    /**
     * @return the scheme this table resolves (without {@code "://"})
     */
    public String scheme() {
        ensureBuilt();
        return scheme;
    }

    /**
     * @return every registered pattern rewritten as an RFC 6570-style template
     *         ({@code :name} &rarr; {@code {name}}), each prefixed with {@code scheme://},
     *         in registration order
     */
    public List<String> templates() {
        ensureBuilt();
        List<String> result = new ArrayList<>(routes.size());
        for (Route r : routes) {
            result.add(scheme + "://" + asTemplate(r));
        }
        return result;
    }

    /**
     * @return the diagnostics computed at {@link #build()} time (route list, parameter usage
     *         counts, guard coverage, shadow warnings) — exposed mainly so tests can assert on
     *         the data behind the startup log lines without scraping log output
     */
    public RouteDiagnostics diagnostics() {
        ensureBuilt();
        return diagnostics;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void ensureBuilt() {
        if (!built) {
            build();
        }
    }

    private void ensureNotBuilt() {
        if (built) {
            throw new IllegalStateException("ResourceRoutes is already built; routes/guards cannot be added after build()");
        }
    }

    private static List<Segment> parsePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("route pattern must not be null or empty");
        }
        String[] parts = pattern.split("/", -1);
        List<Segment> segments = new ArrayList<>(parts.length);
        Set<String> paramNamesSeen = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("route pattern has an empty segment: " + pattern);
            }
            if (part.startsWith(":")) {
                String name = part.substring(1);
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("route pattern has an empty parameter name: " + pattern);
                }
                if (!paramNamesSeen.add(name)) {
                    // A repeated :name within one pattern would silently overwrite the first
                    // binding in Params (LinkedHashMap put semantics) and any guard on it would
                    // only ever see the last segment's value -- a real, cheap-to-catch bug class,
                    // not just a style nit.
                    throw new IllegalArgumentException(
                            "route pattern binds parameter :" + name + " more than once: " + pattern);
                }
                segments.add(new Segment(name, true));
            } else {
                segments.add(new Segment(part, false));
            }
        }
        return segments;
    }

    private boolean passesGuards(Params params) {
        for (GuardEntry g : guards) {
            String value = params.get(g.paramName());
            if (value == null) {
                continue; // this route doesn't bind that name; guard doesn't apply
            }
            if (!g.predicate().test(value)) {
                return false;
            }
        }
        return true;
    }

    private void validateGuardNames() {
        Set<String> allParamNames = new LinkedHashSet<>();
        for (Route r : routes) {
            for (Segment s : r.segments()) {
                if (s.isParam()) {
                    allParamNames.add(s.value());
                }
            }
        }
        for (GuardEntry g : guards) {
            if (!allParamNames.contains(g.paramName())) {
                throw new IllegalStateException(
                        "guard registered for parameter '" + g.paramName() + "' but no route in scheme '"
                                + scheme + "' binds a parameter with that name (typo?)");
            }
        }
    }

    private void detectExactDuplicates() {
        for (int i = 0; i < routes.size(); i++) {
            for (int j = i + 1; j < routes.size(); j++) {
                if (sameShape(routes.get(i), routes.get(j))) {
                    throw new IllegalStateException(
                            "duplicate route shape: '" + routes.get(i).pattern() + "' (registered #" + (i + 1)
                                    + ") and '" + routes.get(j).pattern() + "' (registered #" + (j + 1)
                                    + ") match exactly the same URIs; the second route can never be reached");
                }
            }
        }
    }

    private static boolean sameShape(Route a, Route b) {
        if (a.segments().size() != b.segments().size()) {
            return false;
        }
        for (int i = 0; i < a.segments().size(); i++) {
            Segment sa = a.segments().get(i);
            Segment sb = b.segments().get(i);
            if (sa.isParam() != sb.isParam()) {
                return false;
            }
            if (!sa.isParam() && !sa.value().equals(sb.value())) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if every URI that would match {@code later} is already matched by
     *         {@code earlier} (same segment count, and at every position {@code earlier} is
     *         either a parameter or the identical literal)
     */
    private static boolean covers(Route earlier, Route later) {
        if (earlier.segments().size() != later.segments().size()) {
            return false;
        }
        for (int i = 0; i < earlier.segments().size(); i++) {
            Segment e = earlier.segments().get(i);
            Segment l = later.segments().get(i);
            boolean segmentCovers = e.isParam() || (!l.isParam() && e.value().equals(l.value()));
            if (!segmentCovers) {
                return false;
            }
        }
        return true;
    }

    private List<ShadowWarning> detectShadowedRoutes() {
        List<ShadowWarning> warnings = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            for (int j = i + 1; j < routes.size(); j++) {
                Route earlier = routes.get(i);
                Route later = routes.get(j);
                if (sameShape(earlier, later)) {
                    continue; // reported as a hard error by detectExactDuplicates(), not a warning
                }
                if (covers(earlier, later)) {
                    warnings.add(new ShadowWarning(earlier.pattern(), i + 1, later.pattern(), j + 1));
                }
            }
        }
        return warnings;
    }

    private Map<String, Integer> computeParamUsageCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Route r : routes) {
            Set<String> paramsInRoute = new LinkedHashSet<>();
            for (Segment s : r.segments()) {
                if (s.isParam()) {
                    paramsInRoute.add(s.value());
                }
            }
            for (String p : paramsInRoute) {
                counts.merge(p, 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<GuardCoverage> computeGuardCoverage(Map<String, Integer> paramUsageCounts) {
        List<GuardCoverage> coverage = new ArrayList<>(guards.size());
        for (GuardEntry g : guards) {
            int count = paramUsageCounts.getOrDefault(g.paramName(), 0);
            coverage.add(new GuardCoverage(g.paramName(), count));
        }
        return coverage;
    }

    private RouteDiagnostics computeDiagnostics() {
        List<String> patterns = new ArrayList<>(routes.size());
        for (Route r : routes) {
            patterns.add(r.pattern());
        }
        Map<String, Integer> paramUsageCounts = computeParamUsageCounts();
        List<GuardCoverage> guardCoverage = computeGuardCoverage(paramUsageCounts);
        List<ShadowWarning> shadowWarnings = detectShadowedRoutes();
        return new RouteDiagnostics(patterns, paramUsageCounts, guardCoverage, shadowWarnings);
    }

    private void logDiagnostics(RouteDiagnostics diag) {
        LOG.info("ResourceRoutes[scheme={}]: {} routes registered", scheme, diag.patterns().size());
        for (int i = 0; i < diag.patterns().size(); i++) {
            LOG.info("  route[{}]: {}://{}", i + 1, scheme, diag.patterns().get(i));
        }
        for (Map.Entry<String, Integer> e : diag.paramUsageCounts().entrySet()) {
            LOG.info("  param :{} bound by {} route(s)", e.getKey(), e.getValue());
        }
        for (GuardCoverage gc : diag.guardCoverage()) {
            LOG.info("  guard on :{} applies to {} route(s)", gc.paramName(), gc.routeCount());
        }
        for (ShadowWarning w : diag.shadowWarnings()) {
            LOG.warn("route '{}://{}' (registered #{}) shadows route '{}://{}' (registered #{}); the later route can never be reached",
                    scheme, w.earlierPattern(), w.earlierPosition(), scheme, w.laterPattern(), w.laterPosition());
        }
    }

    private static String asTemplate(Route r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.segments().size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            Segment s = r.segments().get(i);
            sb.append(s.isParam() ? "{" + s.value() + "}" : s.value());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    private record Segment(String value, boolean isParam) {
    }

    private record Route(String pattern, List<Segment> segments, Function<Params, Resource> factory) {

        boolean matches(String[] uriSegments) {
            if (uriSegments.length != segments.size()) {
                return false;
            }
            for (int i = 0; i < uriSegments.length; i++) {
                Segment seg = segments.get(i);
                if (!seg.isParam() && !seg.value().equals(uriSegments[i])) {
                    return false;
                }
            }
            return true;
        }

        Params bind(String[] uriSegments) {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < uriSegments.length; i++) {
                Segment seg = segments.get(i);
                if (seg.isParam()) {
                    map.put(seg.value(), uriSegments[i]);
                }
            }
            return new Params(map);
        }
    }

    private record GuardEntry(String paramName, Predicate<String> predicate) {
    }

    /**
     * One registered guard's coverage: how many routes actually bind the parameter it guards.
     */
    public record GuardCoverage(String paramName, int routeCount) {
    }

    /**
     * A same-length-route shadowing relationship detected at {@link #build()}: {@code earlier}
     * (registered first, 1-based {@code earlierPosition}) already matches every URI that
     * {@code later} (1-based {@code laterPosition}) would ever match, making {@code later}
     * unreachable.
     */
    public record ShadowWarning(String earlierPattern, int earlierPosition, String laterPattern, int laterPosition) {
    }

    /**
     * Snapshot of the route table's shape, computed once at {@link #build()}.
     */
    public record RouteDiagnostics(
            List<String> patterns,
            Map<String, Integer> paramUsageCounts,
            List<GuardCoverage> guardCoverage,
            List<ShadowWarning> shadowWarnings) {
    }
}
