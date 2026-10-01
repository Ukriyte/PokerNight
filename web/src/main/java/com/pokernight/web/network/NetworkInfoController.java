package com.pokernight.web.network;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/network-info")
public final class NetworkInfoController {
    private final LanAddressResolver addresses;

    public NetworkInfoController(LanAddressResolver addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    NetworkInfoResponse get(HttpServletRequest request) {
        String address = addresses.resolve().orElse(request.getServerName());
        String origin = request.getScheme() + "://" + address
            + (isDefaultPort(request.getScheme(), request.getServerPort()) ? "" : ":" + request.getServerPort());
        return new NetworkInfoResponse(origin, address, !isLocalhost(address));
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
    }

    private static boolean isLocalhost(String address) {
        return address.equals("localhost") || address.equals("127.0.0.1") || address.equals("::1");
    }

    record NetworkInfoResponse(String origin, String address, boolean lanAvailable) { }
}
