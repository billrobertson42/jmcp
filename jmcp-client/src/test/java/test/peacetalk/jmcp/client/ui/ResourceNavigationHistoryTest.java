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

package test.peacetalk.jmcp.client.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.ui.ResourceNavigationHistory;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourceNavigationHistory
 */
class ResourceNavigationHistoryTest {

    private ResourceNavigationHistory history;

    @BeforeEach
    void setUp() {
        history = new ResourceNavigationHistory();
    }

    @Test
    void testInitialState() {
        assertFalse(history.canGoBack(), "fresh history cannot go back");
        assertEquals(0, history.historyDepth(), "fresh history has depth 0");
        assertTrue(history.current().isEmpty(), "fresh history has no current entry");
        assertTrue(history.currentUri().isEmpty(), "fresh history has no current URI");
    }

    @Test
    void testGoBackWhenEmpty() {
        Optional<ResourceNavigationHistory.HistoryEntry> result = history.goBack();
        assertTrue(result.isEmpty(), "goBack on empty history returns empty");
        assertTrue(history.current().isEmpty(), "goBack on empty history leaves no current");
    }

    @Test
    void testNavigateToFirst() {
        history.navigateTo("db://connections", "content1");

        assertFalse(history.canGoBack(), "first navigation leaves nothing to go back to");
        assertEquals(0, history.historyDepth(), "first navigation does not push to history");
        assertTrue(history.current().isPresent());
        assertEquals("db://connections", history.currentUri().get());
        assertEquals("content1", history.current().get().displayContent(),
            "current entry should keep the display content passed in");
    }

    @Test
    void testNavigateToSecond() {
        history.navigateTo("db://connections", "content1");
        history.navigateTo("db://connection/db1", "content2");

        assertTrue(history.canGoBack(), "second navigation pushes the first to history");
        assertEquals(1, history.historyDepth());
        assertEquals("db://connection/db1", history.currentUri().get());
        assertEquals("content2", history.current().get().displayContent());
    }

    @Test
    void testNavigateMultiple() {
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");
        history.navigateTo("uri4", "c4");

        assertTrue(history.canGoBack());
        assertEquals(3, history.historyDepth(), "four navigations leave three in back history");
        assertEquals("uri4", history.currentUri().get());
    }

    @Test
    void testGoBackReturnsPreviousEntryAndBecomesCurrent() {
        history.navigateTo("uri1", "content1");
        history.navigateTo("uri2", "content2");

        Optional<ResourceNavigationHistory.HistoryEntry> previous = history.goBack();

        assertTrue(previous.isPresent(), "goBack with history should return an entry");
        assertEquals("uri1", previous.get().uri(), "goBack should return the previous URI");
        assertEquals("content1", previous.get().displayContent(),
            "goBack should return the previous entry's content");
        // The returned entry is also now the current one.
        assertEquals("uri1", history.currentUri().get(), "the previous entry becomes current");
        assertEquals(previous.get(), history.current().get(),
            "goBack's return value and current() should be the same entry");
        assertFalse(history.canGoBack(), "after going back to the first entry, cannot go back");
        assertEquals(0, history.historyDepth());
    }

    @Test
    void testGoBackDiscardsCurrentEntry() {
        // uri2 (the entry we leave via goBack) should not reappear later.
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");

        history.goBack(); // discards uri2, current is now uri1

        assertEquals("uri1", history.currentUri().get());
        // Navigating forward again pushes uri1, so history depth is back to 1 and uri2 is gone.
        history.navigateTo("uri3", "c3");
        assertEquals(1, history.historyDepth());
        Optional<ResourceNavigationHistory.HistoryEntry> back = history.goBack();
        assertEquals("uri1", back.get().uri(), "discarded uri2 must not resurface");
    }

    @Test
    void testGoBackMultiple() {
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");

        assertEquals("uri3", history.currentUri().get());
        assertEquals(2, history.historyDepth());

        history.goBack();
        assertEquals("uri2", history.currentUri().get());
        assertEquals(1, history.historyDepth());

        history.goBack();
        assertEquals("uri1", history.currentUri().get());
        assertEquals(0, history.historyDepth());
        assertFalse(history.canGoBack());
    }

    @Test
    void testGoBackAtBeginningWithSingleEntry() {
        history.navigateTo("uri1", "c1");

        // Only current exists; nothing was ever pushed, so goBack is a no-op returning empty.
        Optional<ResourceNavigationHistory.HistoryEntry> result = history.goBack();
        assertTrue(result.isEmpty(), "goBack with only a current entry returns empty");
        assertEquals("uri1", history.currentUri().get(),
            "a failed goBack must not clear or change the current entry");
    }

    @Test
    void testGoBackPastStartIsStable() {
        // Walk all the way back, then attempt to go back again beyond the start.
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");

        history.goBack(); // now at uri1, history empty

        Optional<ResourceNavigationHistory.HistoryEntry> pastStart = history.goBack();
        assertTrue(pastStart.isEmpty(), "going back past the start returns empty");
        assertEquals("uri1", history.currentUri().get(),
            "current entry must remain uri1 after a back-past-start attempt");
        assertFalse(history.canGoBack());
        assertEquals(0, history.historyDepth());
    }

    @Test
    void testCanGoBackTransitions() {
        assertFalse(history.canGoBack(), "empty: no back");
        history.navigateTo("uri1", "c1");
        assertFalse(history.canGoBack(), "single entry: no back");
        history.navigateTo("uri2", "c2");
        assertTrue(history.canGoBack(), "two entries: can go back");
        history.goBack();
        assertFalse(history.canGoBack(), "back to first entry: no back");
    }

    @Test
    void testClear() {
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");

        history.clear();

        assertFalse(history.canGoBack(), "cleared history cannot go back");
        assertEquals(0, history.historyDepth(), "cleared history has depth 0");
        assertTrue(history.current().isEmpty(), "cleared history has no current entry");
        assertTrue(history.currentUri().isEmpty());
    }

    @Test
    void testNavigateAfterClearStartsFresh() {
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.clear();

        // After clear, the first navigateTo behaves like a fresh start (nothing to push).
        history.navigateTo("uri3", "c3");
        assertEquals("uri3", history.currentUri().get());
        assertEquals(0, history.historyDepth(), "post-clear navigation must not retain old depth");
        assertFalse(history.canGoBack());
    }

    @Test
    void testNavigateAfterGoBackDiscardsForwardHistory() {
        // Navigating after going back discards any "forward" entry (stack model).
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");

        history.goBack(); // Now at uri2, uri3 discarded
        history.navigateTo("uri4", "c4"); // Navigate somewhere new

        assertEquals("uri4", history.currentUri().get());
        assertEquals(2, history.historyDepth(), "history should hold uri1 and uri2");

        // Going back should go to uri2, not the discarded uri3.
        Optional<ResourceNavigationHistory.HistoryEntry> back = history.goBack();
        assertEquals("uri2", back.get().uri(), "discarded uri3 must not reappear");
    }

    @Test
    void testNavigateToNullArgumentsAllowed() {
        // The API does not reject null uri/content; the entry stores them as-is.
        history.navigateTo(null, null);
        assertTrue(history.current().isPresent(), "a null-valued entry is still a current entry");
        assertTrue(history.currentUri().isEmpty(),
            "currentUri maps a null uri to Optional.empty via map()");
        assertNull(history.current().get().displayContent(), "null content is stored verbatim");
    }

    @Test
    void testHistoryEntryRecord() {
        ResourceNavigationHistory.HistoryEntry entry =
            new ResourceNavigationHistory.HistoryEntry("test://uri", "test content");

        assertEquals("test://uri", entry.uri());
        assertEquals("test content", entry.displayContent());
    }
}
