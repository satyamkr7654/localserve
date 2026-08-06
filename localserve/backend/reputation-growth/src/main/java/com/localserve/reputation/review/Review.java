package com.localserve.reputation.review;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class Review {
    private final PublicId id;
    private final PublicId bookingId;
    private final PublicId customerId;
    private final PublicId providerId;
    private final Instant createdAt;
    private int rating;
    private String comment;
    private List<PublicId> imageFileIds;
    private boolean removed;
    private long version;

    private Review(PublicId bookingId, PublicId customerId, PublicId providerId, int rating,
                   String comment, List<PublicId> imageFileIds, Instant createdAt) {
        this.id = PublicId.generate();
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.rating = requireRating(rating);
        this.comment = requireComment(comment);
        this.imageFileIds = requireImages(imageFileIds);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Review createVerified(PublicId bookingId, PublicId customerId, PublicId providerId,
                                        int rating, String comment, List<PublicId> images,
                                        boolean bookingClosed, boolean existingReview, Clock clock) {
        if (!bookingClosed) throw new DomainException("REVIEW.BOOKING_NOT_COMPLETED", "Only closed bookings can be reviewed");
        if (existingReview) throw new DomainException("REVIEW.ALREADY_EXISTS", "This booking has already been reviewed");
        return new Review(bookingId, customerId, providerId, rating, comment, images, clock.instant());
    }

    public void edit(int rating, String comment, List<PublicId> images, Clock clock, Duration editWindow) {
        if (removed || clock.instant().isAfter(createdAt.plus(editWindow))) {
            throw new DomainException("REVIEW.EDIT_WINDOW_CLOSED", "Review can no longer be edited");
        }
        this.rating = requireRating(rating);
        this.comment = requireComment(comment);
        this.imageFileIds = requireImages(images);
        version++;
    }

    public void removeByModerator() { removed = true; version++; }
    private static int requireRating(int value) { if (value < 1 || value > 5) throw new IllegalArgumentException("rating must be 1..5"); return value; }
    private static String requireComment(String value) { String clean = Objects.requireNonNull(value, "comment").strip(); if (clean.length() > 2_000) throw new IllegalArgumentException("comment too long"); return clean; }
    private static List<PublicId> requireImages(List<PublicId> value) { List<PublicId> copy = List.copyOf(value); if (copy.size() > 5) throw new IllegalArgumentException("at most five images are allowed"); return copy; }
    public PublicId id() { return id; }
    public PublicId bookingId() { return bookingId; }
    public int rating() { return rating; }
    public boolean removed() { return removed; }
}
