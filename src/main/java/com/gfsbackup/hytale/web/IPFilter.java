package com.gfsbackup.hytale.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class IPFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(IPFilter.class);

    private final List<String> allowedIPs;

    public IPFilter(List<String> allowedIPs) {
        this.allowedIPs = allowedIPs;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        String remoteAddr = httpReq.getRemoteAddr();

        // Normalize IPv6 loopback to IPv4 for easier matching
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            remoteAddr = "127.0.0.1";
        }

        if (allowedIPs.isEmpty() || allowedIPs.contains(remoteAddr)) {
            chain.doFilter(request, response);
        } else {
            logger.warn("Blocked request from unauthorized IP: {} -> {} {}",
                    httpReq.getRemoteAddr(), httpReq.getMethod(), httpReq.getRequestURI());
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResp.setContentType("text/plain");
            httpResp.setCharacterEncoding("UTF-8");
            httpResp.getWriter().write("403 Forbidden");
            httpResp.getWriter().flush();
            httpResp.getWriter().close();
        }
    }
}
