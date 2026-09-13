package tech.qiantong.qknow.framework.security;

import okhttp3.Dns;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * DNS 解析安全拦截器，防止 SSRF 与 DNS 重绑定攻击。
 * 拦截内网、私网、回环地址，以及元数据服务器地址 (169.254.169.254)。
 */
public class SafeDns implements Dns {
    
    private final Dns delegate;

    public SafeDns() {
        this(Dns.SYSTEM);
    }

    public SafeDns(Dns delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = delegate.lookup(hostname);
        for (InetAddress address : addresses) {
            if (isForbidden(address)) {
                throw new UnknownHostException("DNS resolution blocked: " + address.getHostAddress() + " is a forbidden IP.");
            }
        }
        return addresses;
    }

    private boolean isForbidden(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        byte[] addressBytes = address.getAddress();
        if (addressBytes.length == 4) { // IPv4
            int ip = ((addressBytes[0] & 0xFF) << 24) |
                     ((addressBytes[1] & 0xFF) << 16) |
                     ((addressBytes[2] & 0xFF) << 8)  |
                     (addressBytes[3] & 0xFF);

            // 127.0.0.0/8
            if ((ip & 0xFF000000) == 0x7F000000) return true;
            // 10.0.0.0/8
            if ((ip & 0xFF000000) == 0x0A000000) return true;
            // 172.16.0.0/12
            if ((ip & 0xFFF00000) == 0xAC100000) return true;
            // 192.168.0.0/16
            if ((ip & 0xFFFF0000) == 0xC0A80000) return true;
            // 169.254.0.0/16
            if ((ip & 0xFFFF0000) == 0xA9FE0000) return true;
            // 0.0.0.0/8
            if ((ip & 0xFF000000) == 0x00000000) return true;
        } else if (addressBytes.length == 16) { // IPv6
            // fd00::/8 and fc00::/7 Unique Local Addresses
            if ((addressBytes[0] & 0xFE) == 0xFC) return true;
        }
        return false;
    }
}
