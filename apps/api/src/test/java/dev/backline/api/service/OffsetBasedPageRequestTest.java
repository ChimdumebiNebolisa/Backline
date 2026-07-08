package dev.backline.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OffsetBasedPageRequestTest {

    @Test
    void exposesAbsoluteOffsetSemantics() {
        var pageable = new OffsetBasedPageRequest(10, 25, Sort.by("createdAt"));

        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getOffset()).isEqualTo(25);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.hasPrevious()).isTrue();
    }

    @Test
    void nextAndPreviousShiftByLimit() {
        var first = new OffsetBasedPageRequest(5, 0, Sort.unsorted());
        var next = (OffsetBasedPageRequest) first.next();
        var previous = (OffsetBasedPageRequest) next.previousOrFirst();

        assertThat(next.getOffset()).isEqualTo(5);
        assertThat(previous.getOffset()).isEqualTo(0);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> new OffsetBasedPageRequest(0, 0, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OffsetBasedPageRequest(1, -1, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
