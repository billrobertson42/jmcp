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
        assertFalse(history.canGoBack());
        assertEquals(0, history.historyDepth());
        assertTrue(history.current().isEmpty());
        assertTrue(history.currentUri().isEmpty());
    }

    @Test
    void testNavigateToFirst() {
        history.navigateTo("db://connections", "content1");

        assertFalse(history.canGoBack()); // Still can't go back - nothing in history
        assertEquals(0, history.historyDepth());
        assertTrue(history.current().isPresent());
        assertEquals("db://connections", history.currentUri().get());
    }

    @Test
    void testNavigateToSecond() {
        history.navigateTo("db://connections", "content1");
        history.navigateTo("db://connection/db1", "content2");

        assertTrue(history.canGoBack());
        assertEquals(1, history.historyDepth());
        assertEquals("db://connection/db1", history.currentUri().get());
    }

    @Test
    void testNavigateMultiple() {
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");
        history.navigateTo("uri4", "c4");

        assertTrue(history.canGoBack());
        assertEquals(3, history.historyDepth());
        assertEquals("uri4", history.currentUri().get());
    }

    @Test
    void testGoBack() {
        history.navigateTo("uri1", "content1");
        history.navigateTo("uri2", "content2");

        Optional<ResourceNavigationHistory.HistoryEntry> previous = history.goBack();

        assertTrue(previous.isPresent());
        assertEquals("uri1", previous.get().uri());
        assertEquals("content1", previous.get().displayContent());
        assertEquals("uri1", history.currentUri().get());
        assertFalse(history.canGoBack());
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
    void testGoBackWhenEmpty() {
        Optional<ResourceNavigationHistory.HistoryEntry> result = history.goBack();
        assertTrue(result.isEmpty());
    }

    @Test
    void testGoBackAtBeginning() {
        history.navigateTo("uri1", "c1");

        // Can't go back when there's only current
        Optional<ResourceNavigationHistory.HistoryEntry> result = history.goBack();
        assertTrue(result.isEmpty());
    }

    @Test
    void testClear() {
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");

        history.clear();

        assertFalse(history.canGoBack());
        assertEquals(0, history.historyDepth());
        assertTrue(history.current().isEmpty());
    }

    @Test
    void testNavigateAfterGoBack() {
        // Test that navigating after going back discards forward history
        history.navigateTo("uri1", "c1");
        history.navigateTo("uri2", "c2");
        history.navigateTo("uri3", "c3");

        history.goBack(); // Now at uri2
        history.navigateTo("uri4", "c4"); // Navigate somewhere new

        assertEquals("uri4", history.currentUri().get());
        assertEquals(2, history.historyDepth()); // uri1, uri2 in history

        // Going back should go to uri2, not uri3
        history.goBack();
        assertEquals("uri2", history.currentUri().get());
    }

    @Test
    void testHistoryEntryRecord() {
        ResourceNavigationHistory.HistoryEntry entry =
            new ResourceNavigationHistory.HistoryEntry("test://uri", "test content");

        assertEquals("test://uri", entry.uri());
        assertEquals("test content", entry.displayContent());
    }
}

