package com.pokernight.web.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanAddressResolverTest {
    @Test
    void prefersMacWifiOverOtherPhysicalPrivateInterfaces() {
        var selected = LanAddressResolver.choose(List.of(
            new LanAddressResolver.Candidate("eth0", "172.20.10.4"),
            new LanAddressResolver.Candidate("en1", "192.168.50.4"),
            new LanAddressResolver.Candidate("en0", "192.168.1.9")
        ));
        assertThat(selected).hasValue(new LanAddressResolver.Candidate("en0", "192.168.1.9"));
    }
}
