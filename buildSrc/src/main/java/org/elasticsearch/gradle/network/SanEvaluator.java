package org.elasticsearch.gradle.network;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A lazy evaluator to find the san to use for certificate generation.
 */
public class SanEvaluator {

    private static String san = null;

    public String toString() {
        synchronized (SanEvaluator.class) {
            if (san == null) {
                san = getSubjectAlternativeNameString();
            }
        }
        return san;
    }

    // Code stolen from NetworkUtils/InetAddresses/NetworkAddress to support SAN

    /**
     * Return all interfaces (and subinterfaces) on the system
     */
    private static List<NetworkInterface> getInterfaces() throws SocketException {
        List<NetworkInterface> all = new ArrayList<>();
        addAllInterfaces(all, Collections.list(NetworkInterface.getNetworkInterfaces()));
        Collections.sort(all, new Comparator<NetworkInterface>() {
            @Override
            public int compare(NetworkInterface left, NetworkInterface right) {
                return Integer.compare(left.getIndex(), right.getIndex());
            }
        });
        return all;
    }

    /**
     * Helper for getInterfaces, recursively adds subinterfaces to {@code target}
     */
    private static void addAllInterfaces(List<NetworkInterface> target, List<NetworkInterface> level) {
        if (!level.isEmpty()) {
            target.addAll(level);
            for (NetworkInterface intf : level) {
                addAllInterfaces(target, Collections.list(intf.getSubInterfaces()));
            }
        }
    }

    private static String getSubjectAlternativeNameString() {
        List<InetAddress> list = new ArrayList<>();
        try {

            for (NetworkInterface intf : getInterfaces()) {
                for (final InetAddress address : Collections.list(intf.getInetAddresses())) {
                    /*
                     * Some OS (e.g., BSD) assign a link-local address to the loopback interface.
                     * While technically not a loopback interface, some of these OS treat them as one (e.g., localhost on macOS),
                     * so we must too. Otherwise, things just won't work out of the box. So we include all addresses from
                     * loopback interfaces.
                     *
                     * By checking if the interface is a loopback interface or the address is a loopback address first,
                     * we avoid having to check if the interface is up unless necessary.
                     * This means we can avoid checking if the interface is up for virtual ethernet devices which have
                     * a tendency to disappear outside of our control (e.g., due to Docker).
                     */
                    if ((intf.isLoopback() || address.isLoopbackAddress()) && isUp(intf, address)) {
                        list.add(address);
                    }
                }
            }
            if (list.isEmpty()) {
                throw new IllegalArgumentException("no up-and-running loopback addresses found, got " + getInterfaces());
            }

            StringBuilder builder = new StringBuilder("san=");
            for (int i = 0; i < list.size(); i++) {
                InetAddress address = list.get(i);
                String hostAddress;
                if (address instanceof Inet6Address) {
                    hostAddress = compressedIPV6Address((Inet6Address) address);
                } else {
                    hostAddress = address.getHostAddress();
                }
                builder.append("ip:").append(hostAddress);
                String hostname = address.getHostName();
                if (hostname.equals(address.getHostAddress()) == false) {
                    builder.append(",dns:").append(hostname);
                }

                if (i != (list.size() - 1)) {
                    builder.append(",");
                }
            }

            return builder.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot resolve alternative name string", e);
        }
    }

    private static boolean isUp(final NetworkInterface intf, final InetAddress address) throws IOException {
        try {
            return intf.isUp();
        } catch (final SocketException e) {
            /*
             * In Elasticsearch production code (NetworkUtils) we suppress this if the device is a virtual ethernet device.
             * That should not happen here since the interface must be a loopback device or the address a loopback address
             * to get here to begin with.
             */
            assert intf.isLoopback() || address.isLoopbackAddress();
            throw new IOException("failed to check if interface [" + intf.getName() + "] is up", e);
        }
    }

    private static String compressedIPV6Address(Inet6Address inet6Address) {
        byte[] bytes = inet6Address.getAddress();
        int[] hextets = new int[8];
        for (int i = 0; i < hextets.length; i++) {
            hextets[i] = (bytes[2 * i] & 255) << 8 | bytes[2 * i + 1] & 255;
        }
        compressLongestRunOfZeroes(hextets);
        return hextetsToIPv6String(hextets);
    }

    /**
     * Identify and mark the longest run of zeroes in an IPv6 address.
     *
     * <p>Only runs of two or more hextets are considered.  In case of a tie, the
     * leftmost run wins.  If a qualifying run is found, its hextets are replaced
     * by the sentinel value -1.
     *
     * @param hextets {@code int[]} mutable array of eight 16-bit hextets
     */
    private static void compressLongestRunOfZeroes(int[] hextets) {
        int bestRunStart = -1;
        int bestRunLength = -1;
        int runStart = -1;
        for (int i = 0; i < hextets.length + 1; i++) {
            if (i < hextets.length && hextets[i] == 0) {
                if (runStart < 0) {
                    runStart = i;
                }
            } else if (runStart >= 0) {
                int runLength = i - runStart;
                if (runLength > bestRunLength) {
                    bestRunStart = runStart;
                    bestRunLength = runLength;
                }
                runStart = -1;
            }
        }
        if (bestRunLength >= 2) {
            Arrays.fill(hextets, bestRunStart, bestRunStart + bestRunLength, -1);
        }
    }

    /**
     * Convert a list of hextets into a human-readable IPv6 address.
     *
     * <p>In order for "::" compression to work, the input should contain negative
     * sentinel values in place of the elided zeroes.
     *
     * @param hextets {@code int[]} array of eight 16-bit hextets, or -1s
     */
    private static String hextetsToIPv6String(int[] hextets) {
        /*
         * While scanning the array, handle these state transitions:
         *   start->num => "num"     start->gap => "::"
         *   num->num   => ":num"    num->gap   => "::"
         *   gap->num   => "num"     gap->gap   => ""
         */
        StringBuilder buf = new StringBuilder(39);
        boolean lastWasNumber = false;
        for (int i = 0; i < hextets.length; i++) {
            boolean thisIsNumber = hextets[i] >= 0;
            if (thisIsNumber) {
                if (lastWasNumber) {
                    buf.append(':');
                }
                buf.append(Integer.toHexString(hextets[i]));
            } else {
                if (i == 0 || lastWasNumber) {
                    buf.append("::");
                }
            }
            lastWasNumber = thisIsNumber;
        }
        return buf.toString();
    }
}
