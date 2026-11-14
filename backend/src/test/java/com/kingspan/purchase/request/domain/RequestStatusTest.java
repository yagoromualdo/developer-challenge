package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RequestStatusTest {

    @Test
    void deveConsiderarFinaisApenasApprovedRejectedECancelled() {
        assertThat(Arrays.stream(RequestStatus.values()).filter(RequestStatus::isFinal))
                .containsExactlyInAnyOrder(RequestStatus.APPROVED, RequestStatus.REJECTED, RequestStatus.CANCELLED);
    }
}
