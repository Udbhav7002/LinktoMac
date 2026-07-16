#include "network.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#include <iostream>
#include <cstring>
#include <cerrno>

TcpConnection::TcpConnection() : sock_fd_(-1), server_fd_(-1) {}

TcpConnection::~TcpConnection() {
    disconnect();
    stop_listening();
}

void TcpConnection::apply_socket_options() {
    // Disable Nagle's algorithm for low latency
    int flag = 1;
    if (setsockopt(sock_fd_, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(int)) < 0) {
        std::cerr << "Failed to set TCP_NODELAY: " << strerror(errno) << "\n";
        return;
    }

    // Large receive buffer for smooth streaming (256KB)
    int rcvbuf = 262144;
    if (setsockopt(sock_fd_, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf)) < 0) {
        std::cerr << "Failed to set SO_RCVBUF: " << strerror(errno) << "\n";
        return;
    }
    
    // Enable TCP keepalive to detect dead peers
    int keepalive = 1;
    if (setsockopt(sock_fd_, SOL_SOCKET, SO_KEEPALIVE, &keepalive, sizeof(keepalive)) < 0) {
        std::cerr << "Failed to set SO_KEEPALIVE: " << strerror(errno) << "\n";
    }
    
#ifdef TCP_KEEPIDLE
    int keepidle = 10; // 10 seconds
    setsockopt(sock_fd_, IPPROTO_TCP, TCP_KEEPIDLE, &keepidle, sizeof(keepidle));
#endif
#ifdef TCP_KEEPINTVL
    int keepintvl = 3; // 3 seconds
    setsockopt(sock_fd_, IPPROTO_TCP, TCP_KEEPINTVL, &keepintvl, sizeof(keepintvl));
#endif
#ifdef TCP_KEEPCNT
    int keepcnt = 3; // 3 probes
    setsockopt(sock_fd_, IPPROTO_TCP, TCP_KEEPCNT, &keepcnt, sizeof(keepcnt));
#endif

    // Set 5-second timeout for read/write to prevent deadlocks on silent disconnects
    struct timeval tv;
    tv.tv_sec = 5;
    tv.tv_usec = 0;
    if (setsockopt(sock_fd_, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv)) < 0) {
        std::cerr << "Failed to set SO_RCVTIMEO: " << strerror(errno) << "\n";
        return;
    }
    if (setsockopt(sock_fd_, SOL_SOCKET, SO_SNDTIMEO, (const char*)&tv, sizeof(tv)) < 0) {
        std::cerr << "Failed to set SO_SNDTIMEO: " << strerror(errno) << "\n";
        return;
    }
}

bool TcpConnection::listen_and_accept(int port) {
    if (server_fd_ < 0) {
        server_fd_ = socket(AF_INET, SOCK_STREAM, 0);
        if (server_fd_ < 0) {
            std::cerr << "[ERROR] Failed to create socket" << std::endl;
            return false;
        }

        int opt = 1;
        setsockopt(server_fd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        struct sockaddr_in address;
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = INADDR_ANY;
        address.sin_port = htons(port);

        if (bind(server_fd_, (struct sockaddr*)&address, sizeof(address)) < 0) {
            std::cerr << "[ERROR] Bind failed on port " << port << std::endl;
            close(server_fd_);
            server_fd_ = -1;
            return false;
        }

        if (listen(server_fd_, 5) < 0) { // Allow backlog
            std::cerr << "[ERROR] Listen failed" << std::endl;
            close(server_fd_);
            server_fd_ = -1;
            return false;
        }
        std::cout << "[INFO] Listening on port " << port << " for incoming remote connection..." << std::endl;
    }
    
    struct sockaddr_in address;
    socklen_t addrlen = sizeof(address);
    sock_fd_ = accept(server_fd_, (struct sockaddr*)&address, &addrlen);
    
    if (sock_fd_ < 0) {
        std::cerr << "[ERROR] Accept failed" << std::endl;
        return false;
    }

    apply_socket_options();
    std::cout << "[INFO] Remote Android client connected!" << std::endl;
    return true;
}

bool TcpConnection::connect(const std::string& host, int port) {
    // Use getaddrinfo for IPv4/IPv6 support
    struct addrinfo hints{}, *res = nullptr, *rp;
    hints.ai_family = AF_UNSPEC;    // IPv4 or IPv6
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_NUMERICSERV;
    
    int err = getaddrinfo(host.c_str(), std::to_string(port).c_str(), &hints, &res);
    if (err != 0) {
        std::cerr << "[ERROR] getaddrinfo failed: " << gai_strerror(err) << std::endl;
        return false;
    }

    bool connected = false;
    for (rp = res; rp != nullptr; rp = rp->ai_next) {
        sock_fd_ = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
        if (sock_fd_ < 0) continue;

        if (::connect(sock_fd_, rp->ai_addr, rp->ai_addrlen) == 0) {
            connected = true;
            break;
        }
        
        close(sock_fd_);
        sock_fd_ = -1;
    }
    
    freeaddrinfo(res);
    
    if (!connected) {
        std::cerr << "Connection to " << host << ":" << port << " failed\n";
        return false;
    }

    apply_socket_options();
    std::cout << "Connected to " << host << ":" << port << "\n";
    return true;
}

void TcpConnection::disconnect() {
    if (sock_fd_ >= 0) {
        close(sock_fd_);
        sock_fd_ = -1;
    }
}

void TcpConnection::stop_listening() {
    if (server_fd_ >= 0) {
        close(server_fd_);
        server_fd_ = -1;
    }
}

bool TcpConnection::recv_exact(uint8_t* buf, int len) {
    int total_read = 0;
    while (total_read < len) {
        int bytes = ::recv(sock_fd_, buf + total_read, len - total_read, 0);
        if (bytes < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Timeout - treat as retryable, not fatal
                continue;
            }
            std::cerr << "recv error: " << strerror(errno) << "\n";
            return false;
        }
        if (bytes == 0) {
            // Connection closed by peer
            return false;
        }
        total_read += bytes;
    }
    return true;
}

int TcpConnection::recv(uint8_t* buf, int len) {
    int bytes = ::recv(sock_fd_, buf, len, 0);
    if (bytes < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
        return 0; // Timeout, no data available
    }
    return bytes;
}

bool TcpConnection::send_all(const uint8_t* buf, int len) {
    int total_sent = 0;
    while (total_sent < len) {
        int bytes = ::send(sock_fd_, buf + total_sent, len - total_sent, 0);
        if (bytes < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                continue;
            }
            std::cerr << "send error: " << strerror(errno) << "\n";
            return false;
        }
        if (bytes == 0) return false;
        total_sent += bytes;
    }
    return true;
}
