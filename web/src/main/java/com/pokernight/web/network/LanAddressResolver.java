package com.pokernight.web.network;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Selects a phone-reachable private IPv4 address while excluding VPN and virtual adapters. */
@Component
public final class LanAddressResolver {
    private static final Pattern VIRTUAL = Pattern.compile(
        "^(lo|utun|awdl|llw|bridge|vmnet|vbox|docker|br-|virbr|gif|stf|ap\\d)",
        Pattern.CASE_INSENSITIVE
    );

    public Optional<String> resolve() {
        List<Candidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()
                    || VIRTUAL.matcher(network.getName()).find()) continue;
                Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        candidates.add(new Candidate(network.getName(), address.getHostAddress()));
                    }
                }
            }
        } catch (SocketException ignored) {
            return Optional.empty();
        }
        return choose(candidates).map(Candidate::address);
    }

    static Optional<Candidate> choose(List<Candidate> candidates) {
        return candidates.stream().min(Comparator
            .comparingInt((Candidate candidate) -> interfacePriority(candidate.interfaceName()))
            .thenComparing(Candidate::interfaceName));
    }

    private static int interfacePriority(String name) {
        if (name.equalsIgnoreCase("en0")) return 0;
        if (name.matches("(?i)en\\d+|wlan\\d+|wi-?fi")) return 1;
        if (name.matches("(?i)eth\\d+")) return 2;
        return 3;
    }

    record Candidate(String interfaceName, String address) { }
}
