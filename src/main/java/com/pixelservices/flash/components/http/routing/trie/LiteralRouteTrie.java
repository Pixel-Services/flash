package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;
import com.pixelservices.logger.Logger;

public class LiteralRouteTrie extends AbstractRadixRouteTrie<RouteEntry> {
    Logger logger = Logger.getLogger(LiteralRouteTrie.class);

    @Override
    protected RouteEntry matchResult(RouteEntry candidate, String fullPath) {
        return candidate != null && candidate.getPath().equals(fullPath) ? candidate : null;
    }

    @Override
    protected boolean isParameterSegment(String segment) {
        return false;
    }

    @Override
    protected String extractParameterName(String segment) {
        return null;
    }

    @Override
    public RouteEntry search(String path) {
        long startTime = System.nanoTime();
        Node<RouteEntry> current = root;
        String[] segments = path.split("/");

        for (String segment : segments) {
            Node<RouteEntry> next = current.children.get(segment);
            if (next == null) {
                long endTime = System.nanoTime();
                logger.info("Literal search operation took " + (endTime - startTime) + " ns");
                return null;
            }
            current = next;
        }

        long endTime = System.nanoTime();
        logger.info("Literal search operation took " + (endTime - startTime) + " ns");
        return current.value;
    }
}


