package tech.qiantong.qknow.framework.security;

import okhttp3.Dns;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SafeDnsTest {

    @Test
    public void testSafeDnsBlocksLoopback() {
        Dns mockDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                return Arrays.asList(InetAddress.getByName("127.0.0.1"));
            }
        };

        SafeDns safeDns = new SafeDns(mockDns);
        assertThrows(UnknownHostException.class, () -> safeDns.lookup("localhost"));
    }

    @Test
    public void testSafeDnsBlocksMetadataServer() {
        Dns mockDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                return Arrays.asList(InetAddress.getByName("169.254.169.254"));
            }
        };

        SafeDns safeDns = new SafeDns(mockDns);
        assertThrows(UnknownHostException.class, () -> safeDns.lookup("metadata.internal"));
    }

    @Test
    public void testSafeDnsAllowsPublicIp() {
        Dns mockDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                return Arrays.asList(InetAddress.getByName("8.8.8.8"));
            }
        };

        SafeDns safeDns = new SafeDns(mockDns);
        assertDoesNotThrow(() -> {
            List<InetAddress> addresses = safeDns.lookup("dns.google");
            assertEquals(1, addresses.size());
            assertEquals("8.8.8.8", addresses.get(0).getHostAddress());
        });
    }
}
