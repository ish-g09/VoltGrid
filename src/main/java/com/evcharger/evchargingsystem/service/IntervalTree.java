package com.evcharger.evchargingsystem.service;

import com.evcharger.evchargingsystem.model.IntervalNode;
import java.time.Instant;

public class IntervalTree {
    private IntervalNode root;

    public boolean isOverlapping(Instant start, Instant end) {
        return checkOverlap(root, start, end);
    }

    private boolean checkOverlap(IntervalNode node, Instant start, Instant end) {
        if (node == null) return false;

        // Overlap condition: start < node.end AND node.start < end
        if (start.isBefore(node.end) && node.start.isBefore(end)) {
            return true;
        }

        // Search left subtree if its maxEnd is after the requested start time
        if (node.left != null && node.left.maxEnd.isAfter(start)) {
            if (checkOverlap(node.left, start, end)) {
                return true;
            }
        }

        return checkOverlap(node.right, start, end);
    }

    public void insert(Instant start, Instant end, String reservationId, String bayId) {
        root = insertNode(root, start, end, reservationId, bayId);
    }

    private IntervalNode insertNode(IntervalNode node, Instant start, Instant end, String reservationId, String bayId) {
        if (node == null) {
            return new IntervalNode(start, end, reservationId, bayId);
        }

        if (start.isBefore(node.start)) {
            node.left = insertNode(node.left, start, end, reservationId, bayId);
        } else {
            node.right = insertNode(node.right, start, end, reservationId, bayId);
        }

        updateMaxEnd(node);
        return node;
    }

    /**
     * O(log N) deletion of a reservation with subtree maxEnd maintenance
     */
    public boolean delete(String reservationId) {
        boolean[] deleted = new boolean[1];
        root = deleteNode(root, reservationId, deleted);
        return deleted[0];
    }

    private IntervalNode deleteNode(IntervalNode node, String reservationId, boolean[] deleted) {
        if (node == null) return null;

        if (reservationId.equals(node.reservationId)) {
            deleted[0] = true;
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;

            // Inorder successor
            IntervalNode successor = getMin(node.right);
            node.start = successor.start;
            node.end = successor.end;
            node.reservationId = successor.reservationId;
            node.bayId = successor.bayId;

            node.right = deleteNode(node.right, successor.reservationId, new boolean[1]);
        } else {
            node.left = deleteNode(node.left, reservationId, deleted);
            if (!deleted[0]) {
                node.right = deleteNode(node.right, reservationId, deleted);
            }
        }

        updateMaxEnd(node);
        return node;
    }

    private IntervalNode getMin(IntervalNode node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    private void updateMaxEnd(IntervalNode node) {
        if (node == null) return;
        node.maxEnd = node.end;
        if (node.left != null && node.left.maxEnd.isAfter(node.maxEnd)) {
            node.maxEnd = node.left.maxEnd;
        }
        if (node.right != null && node.right.maxEnd.isAfter(node.maxEnd)) {
            node.maxEnd = node.right.maxEnd;
        }
    }
}