package tech.qiantong.qknow.common.security.ssrf;

import okhttp3.Dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 传输层 DNS 解析安全拦截器
 * 拦截内网、私网、回环地址、运营商 NAT 以及云厂商元数据服务器地址 (169.254.169.254)
 * 终结 DNS Rebinding 与 TOCTOU 竞争漏洞
 */
public class SafeDns implements Dns {

    private final Dns delegate;

    public SafeDns() {
        this(Dns.SYSTEM);
    }

    public SafeDns(Dns delegate) {
        this.delegate = delegate != null ? delegate : Dns.SYSTEM;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null || hostname.trim().isEmpty()) {
            throw new UnknownHostException("SSRF 拦截：目标主机名为空");
        }

        List<InetAddress> addresses = delegate.lookup(hostname);
        if (addresses == null || addresses.isEmpty()) {
            throw new UnknownHostException("未解析到任何 IP 地址: " + hostname);
        }

        List<InetAddress> safeAddresses = new ArrayList<>();
        for (InetAddress address : addresses) {
            if (isForbidden(address)) {
                throw new UnknownHostException("SSRF 拦截：解析目标存在内网敏感或保留地址: " + address.getHostAddress());
            }
            safeAddresses.add(address);
        }

        if (safeAddresses.isEmpty()) {
            throw new UnknownHostException("SSRF 拦截：目标主机全部 IP 均属于受限私网: " + hostname);
        }

        return safeAddresses;
    }

    /**
     * 判断目标 IP 是否属于受限地址范围
     */
    public static boolean isForbidden(InetAddress address) {
        if (address == null) {
            return true;
        }

        // 基础特征校验
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] addressBytes = address.getAddress();
        if (addressBytes.length == 4) { // IPv4
            int ip = ((addressBytes[0] & 0xFF) << 24) |
                    ((addressBytes[1] & 0xFF) << 16) |
                    ((addressBytes[2] & 0xFF) << 8) |
                    (addressBytes[3] & 0xFF);

            // 0.0.0.0/8 (当前网络)
            if ((ip & 0xFF000000) == 0x00000000) return true;
            // 127.0.0.0/8 (回环地址)
            if ((ip & 0xFF000000) == 0x7F000000) return true;
            // 10.0.0.0/8 (私有网络 A 类)
            if ((ip & 0xFF000000) == 0x0A000000) return true;
            // 172.16.0.0/12 (私有网络 B 类)
            if ((ip & 0xFFF00000) == 0xAC100000) return true;
            // 192.168.0.0/16 (私有网络 C 类)
            if ((ip & 0xFFFF0000) == 0xC0A80000) return true;
            // 169.254.0.0/16 (链路本地 / 云元数据服务 169.254.169.254)
            if ((ip & 0xFFFF0000) == 0xA9FE0000) return true;
            // 100.64.0.0/10 (运营商级 NAT - RFC 6598)
            if ((ip & 0xFFC00000) == 0x64400000) return true;
            // 198.18.0.0/15 (基准测试网段 - RFC 2544)
            if ((ip & 0xFFFE0000) == 0xC6120000) return true;

        } else if (addressBytes.length == 16) { // IPv6
            // ::1 (IPv6 回环)
            boolean isAllZeroExceptLast = true;
            for (int i = 0; i < 15; i++) {
                if (addressBytes[i] != 0) {
                    isAllZeroExceptLast = false;
                    break;
                }
            }
            if (isAllZeroExceptLast && addressBytes[15] == 1) return true;

            // fc00::/7 与 fd00::/8 (唯一本地地址 ULA - RFC 4193)
            if ((addressBytes[0] & 0xFE) == 0xFC) return true;

            // fe80::/10 (链路本地地址 Link-Local - RFC 4291)
            if ((addressBytes[0] & 0xFF) == 0xFE && (addressBytes[1] & 0xC0) == 0x80) return true;

            // IPv4-Mapped IPv6 (::ffff:x.x.x.x)
            boolean isMapped = true;
            for (int i = 0; i < 10; i++) {
                if (addressBytes[i] != 0) {
                    isMapped = false;
                    break;
                }
            }
            if (isMapped && (addressBytes[10] & 0xFF) == 0xFF && (addressBytes[11] & 0xFF) == 0xFF) {
                byte[] ipv4Bytes = new byte[4];
                System.arraycopy(addressBytes, 12, ipv4Bytes, 0, 4);
                try {
                    return isForbidden(InetAddress.getByAddress(ipv4Bytes));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
