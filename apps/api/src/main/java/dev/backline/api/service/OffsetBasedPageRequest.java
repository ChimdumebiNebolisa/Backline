package dev.backline.api.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.Serial;
import java.io.Serializable;

/**
 * Pageable implementation using absolute row offset semantics.
 */
public final class OffsetBasedPageRequest implements Pageable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int limit;
    private final long offset;
    private final Sort sort;

    public OffsetBasedPageRequest(int limit, long offset, Sort sort) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        this.limit = limit;
        this.offset = offset;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetBasedPageRequest(limit, offset + limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        long nextOffset = Math.max(0, offset - limit);
        return new OffsetBasedPageRequest(limit, nextOffset, sort);
    }

    @Override
    public Pageable first() {
        return new OffsetBasedPageRequest(limit, 0, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        return new OffsetBasedPageRequest(limit, (long) pageNumber * limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
