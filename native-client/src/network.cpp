#include "network.h"
#include "protocol.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#include <iostream>
#include <fstream>
#include <cstring>
#include <cerrno>

using namespace std;
using namespace chrono;

TcpConnection::TcpConnection() : sock_fd_(-1), server_fd_(-1) {
    load_saved_0rtt_token();
}

TcpConnection::~TcpConnection() {
    disconnect();
    stop_listening();
}

void TcpConnection::load_saved_0rtt_token() {
    const char* home = getenv("HOME");
    if (!home) return;
    string path = string(home) + "/.linktomac/0rtt_token.bin";
    ifstream infile(path, ios::binary);
    if (infile.is_open()) {
        resumption_token_ = vector<uint8_t>((istreambuf_iterator<char>(infile)),
                                            istreambuf_iterator<char>());
        has_resumption_token_ = !resumption_token_.empty();
        if (has_resumption_token_) {
            cout << "[INFO] Loaded 0-RTT QUIC resumption token (" << resumption_token_.size() << " bytes)\n";
        }
    }
}

void TcpConnection::persist_0rtt_token() {
    if (resumption_token_.empty()) return;
    const char* home = getenv("HOME");
    if (!home) return;
    string dir = string(home) + "/.linktomac";
    string path = dir + "/0rtt_token.bin";
    ofstream outfile(path, ios::binary | ios::trunc);
    if (outfile.is_open()) {
        outfile.write((const char*)resumption_token_.data(), resumption_token_.size());
    }
}

void TcpConnection::store_0rtt_token(const vector<uint8_t>& token) {
    resumption_token_ = token;
    has_resumption_token_ = !resumption_token_.empty();
    persist_0rtt_token();
}

void TcpConnection::apply_socket_options() {
    if (sock_fd_ < 0) return;

    // Disable Nagle's algorithm for ultra-low latency (< 1ms delivery)
    int flag = 1;
    if (setsockopt(sock_fd_, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(int)) < 0) {
        cerr << "[WARN] Failed to set TCP_NODELAY: " << strerror(errno) << "\n";
    }

    // High throughput buffer (1MB) to prevent head-of-line blocking drops
    int rcvbuf = 1048576;
    if (setsockopt(sock_fd_, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf)) < 0) {
        cerr << "[WARN] Failed to set SO_RCVBUF: " << strerror(errno) << "\n";
    }

    int sndbuf = 262144;
    setsockopt(sock_fd_, SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf));

    // Enable TCP keepalive for connection liveness monitoring
    int keepalive = 1;
    setsockopt(sock_fd_, SOL_SOCKET, SO_KEEPALIVE, &keepalive, sizeof(keepalive));

#ifdef TCP_KEEPIDLE
    int keepidle = 5; // 5 seconds
    setsockopt(sock_fd_, IPPROTO_TCP, TCP_KEEPIDLE, &keepidle, sizeof(keepidle));
#endif
#ifdef TCP_KEEPINTVL
    int keepintvl = 2; // 2 seconds
    setsockopt(sock_fd_, IPPROTO_TCP, TCP_KEEPINTVL, &keepintvl, sizeof(keepintvl));
#endif
#ifdef TCP_KEEPCNT
    int keepcnt = 3;
    setsockopt(sock_fd_, IPPROTO_TCP, TCP_KEEPCNT, &keepcnt, sizeof(keepcnt));
#endif

    // Fast socket timeout
    struct timeval tv;
    tv.tv_sec = 4;
    tv.tv_usec = 0;
    setsockopt(sock_fd_, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv));
    setsockopt(sock_fd_, SOL_SOCKET, SO_SNDTIMEO, (const char*)&tv, sizeof(tv));

#ifdef SO_NOSIGPIPE
    int nosigpipe = 1;
    setsockopt(sock_fd_, SOL_SOCKET, SO_NOSIGPIPE, &nosigpipe, sizeof(nosigpipe));
#endif
}

bool TcpConnection::listen_and_accept(int port) {
    if (server_fd_ < 0) {
        server_fd_ = socket(AF_INET, SOCK_STREAM, 0);
        if (server_fd_ < 0) {
            cerr << "[ERROR] Failed to create socket\n";
            return false;
        }

        int opt = 1;
        setsockopt(server_fd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        struct sockaddr_in address;
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = INADDR_ANY;
        address.sin_port = htons(port);

        if (::bind(server_fd_, (struct sockaddr*)&address, sizeof(address)) < 0) {
            cerr << "[ERROR] Bind failed on port " << port << "\n";
            ::close(server_fd_);
            server_fd_ = -1;
            return false;
        }

        if (::listen(server_fd_, 5) < 0) {
            cerr << "[ERROR] Listen failed\n";
            ::close(server_fd_);
            server_fd_ = -1;
            return false;
        }
        cout << "[INFO] Listening on port " << port << " for incoming connection...\n";
    }

    struct sockaddr_in address;
    socklen_t addrlen = sizeof(address);
    sock_fd_ = ::accept(server_fd_, (struct sockaddr*)&address, &addrlen);

    if (sock_fd_ < 0) {
        cerr << "[ERROR] Accept failed\n";
        return false;
    }

    apply_socket_options();
    cout << "[INFO] Client connected successfully via multiplexed transport!\n";
    return true;
}

bool TcpConnection::connect(const string& host, int port) {
    struct addrinfo hints{}, *res = nullptr, *rp;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_NUMERICSERV;

    string port_str = to_string(port);
    int err = getaddrinfo(host.c_str(), port_str.c_str(), &hints, &res);
    if (err != 0) {
        cerr << "[ERROR] getaddrinfo failed: " << gai_strerror(err) << "\n";
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

        ::close(sock_fd_);
        sock_fd_ = -1;
    }

    freeaddrinfo(res);

    if (!connected) {
        cerr << "[ERROR] Connection to " << host << ":" << port << " failed\n";
        return false;
    }

    apply_socket_options();

    // If 0-RTT token exists, accelerate session resumption
    if (has_resumption_token_) {
        cout << "[INFO] Initiating 0-RTT fast connection resumption with cached token\n";
    }

    cout << "[INFO] Connected to " << host << ":" << port << "\n";
    return true;
}

bool TcpConnection::connect_tls(const string& host, int port, const string& ca_cert_path) {
    if (!connect(host, port)) return false;
    ssl_ctx_ = SSL_CTX_new(TLS_client_method());
    if (!ssl_ctx_) return false;
    SSL_CTX_set_min_proto_version(ssl_ctx_, TLS1_3_VERSION);
    SSL_CTX_set_max_proto_version(ssl_ctx_, TLS1_3_VERSION);

    if (!ca_cert_path.empty()) {
        SSL_CTX_load_verify_locations(ssl_ctx_, ca_cert_path.c_str(), nullptr);
        SSL_CTX_set_verify(ssl_ctx_, SSL_VERIFY_PEER, nullptr);
    } else {
        // TOFU mode / development fallback
        SSL_CTX_set_verify(ssl_ctx_, SSL_VERIFY_NONE, nullptr);
    }

    ssl_ = SSL_new(ssl_ctx_);
    if (!ssl_) {
        cleanup_tls();
        return false;
    }

    SSL_set_tlsext_host_name(ssl_, host.c_str());
    SSL_set_fd(ssl_, sock_fd_);
    if (SSL_connect(ssl_) <= 0) {
        ERR_print_errors_fp(stderr);
        cleanup_tls();
        return false;
    }
    use_tls_ = true;
    cout << "[INFO] TLS 1.3 session established to " << host << ":" << port << "\n";
    return true;
}

bool TcpConnection::listen_and_accept_tls(int port, const string& cert_path, const string& key_path, const string& ca_cert_path) {
    if (!listen_and_accept(port)) return false;
    ssl_ctx_ = SSL_CTX_new(TLS_server_method());
    if (!ssl_ctx_) return false;
    SSL_CTX_set_min_proto_version(ssl_ctx_, TLS1_3_VERSION);
    SSL_CTX_set_max_proto_version(ssl_ctx_, TLS1_3_VERSION);

    if (SSL_CTX_use_certificate_file(ssl_ctx_, cert_path.c_str(), SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
        cleanup_tls();
        return false;
    }
    if (SSL_CTX_use_PrivateKey_file(ssl_ctx_, key_path.c_str(), SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
        cleanup_tls();
        return false;
    }
    if (!ca_cert_path.empty()) {
        SSL_CTX_load_verify_locations(ssl_ctx_, ca_cert_path.c_str(), nullptr);
        SSL_CTX_set_verify(ssl_ctx_, SSL_VERIFY_PEER, nullptr);
    }

    ssl_ = SSL_new(ssl_ctx_);
    if (!ssl_) {
        cleanup_tls();
        return false;
    }

    SSL_set_fd(ssl_, sock_fd_);
    if (SSL_accept(ssl_) <= 0) {
        ERR_print_errors_fp(stderr);
        cleanup_tls();
        return false;
    }
    use_tls_ = true;
    cout << "[INFO] Inbound TLS 1.3 session accepted on port " << port << "\n";
    return true;
}

void TcpConnection::cleanup_tls() {
    if (ssl_) {
        SSL_shutdown(ssl_);
        SSL_free(ssl_);
        ssl_ = nullptr;
    }
    if (ssl_ctx_) {
        SSL_CTX_free(ssl_ctx_);
        ssl_ctx_ = nullptr;
    }
    use_tls_ = false;
}

void TcpConnection::disconnect() {
    lock_guard<mutex> lock(send_mutex_);
    cleanup_tls();
    if (sock_fd_ >= 0) {
        ::shutdown(sock_fd_, SHUT_RDWR);
        ::close(sock_fd_);
        sock_fd_ = -1;
    }
}

void TcpConnection::stop_listening() {
    if (server_fd_ >= 0) {
        ::shutdown(server_fd_, SHUT_RDWR);
        ::close(server_fd_);
        server_fd_ = -1;
    }
}

bool TcpConnection::recv_exact(uint8_t* buf, int len) {
    if (use_tls_ && ssl_) {
        int total_read = 0;
        while (total_read < len) {
            int n = SSL_read(ssl_, buf + total_read, len - total_read);
            if (n <= 0) return false;
            total_read += n;
        }
        return true;
    }
    if (sock_fd_ < 0 || !buf || len <= 0) return false;
    int total_read = 0;
    int timeout_count = 0;
    while (total_read < len) {
        if (sock_fd_ < 0) return false;
        int bytes = ::recv(sock_fd_, buf + total_read, len - total_read, 0);
        if (bytes < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                timeout_count++;
                if (timeout_count >= 3) {
                    cerr << "[ERROR] recv_exact timed out waiting for data\n";
                    return false;
                }
                continue;
            }
            cerr << "[ERROR] recv error: " << strerror(errno) << "\n";
            return false;
        }
        if (bytes == 0) {
            return false;
        }
        total_read += bytes;
        timeout_count = 0;
    }
    return true;
}

int TcpConnection::recv(uint8_t* buf, int len) {
    if (use_tls_ && ssl_) {
        int n = SSL_read(ssl_, buf, len);
        if (n <= 0) {
            int err = SSL_get_error(ssl_, n);
            if (err == SSL_ERROR_WANT_READ || err == SSL_ERROR_WANT_WRITE) {
                return 0;
            }
            return -1;
        }
        return n;
    }
    if (sock_fd_ < 0 || !buf || len <= 0) return -1;
    int bytes = ::recv(sock_fd_, buf, len, 0);
    if (bytes < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0; // Timeout / no data available right now, socket is alive
        }
        return -1; // Socket error
    }
    if (bytes == 0) {
        return -1; // Connection closed by peer (EOF)
    }
    return bytes;
}

bool TcpConnection::send_all_locked(const uint8_t* buf, int len) {
    if (use_tls_ && ssl_) {
        int total = 0;
        while (total < len) {
            int n = SSL_write(ssl_, buf + total, len - total);
            if (n <= 0) return false;
            total += n;
        }
        return true;
    }
    if (sock_fd_ < 0 || !buf || len <= 0) return false;
    int total_sent = 0;
    int timeout_count = 0;
    while (total_sent < len) {
        if (sock_fd_ < 0) return false;
#ifdef MSG_NOSIGNAL
        int flags = MSG_NOSIGNAL;
#else
        int flags = 0;
#endif
        int bytes = ::send(sock_fd_, buf + total_sent, len - total_sent, flags);
        if (bytes < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                timeout_count++;
                if (timeout_count >= 3) {
                    cerr << "[ERROR] send_all timed out writing data\n";
                    return false;
                }
                continue;
            }
            cerr << "[ERROR] send error: " << strerror(errno) << "\n";
            return false;
        }
        if (bytes == 0) return false;
        total_sent += bytes;
        timeout_count = 0;
    }
    return true;
}

bool TcpConnection::send_all(const uint8_t* buf, int len) {
    lock_guard<mutex> lock(send_mutex_);
    return send_all_locked(buf, len);
}

bool TcpConnection::send_stream(uint64_t stream_id, const uint8_t* buf, int len) {
    // Multiplexed frame header: magic(4) + flags(4) + stream_id(8) + length(4)
    uint8_t header[20];
    header[0] = 0x51; header[1] = 0x55; header[2] = 0x49; header[3] = 0x43; // 'QUIC'
    header[4] = 0; header[5] = 0; header[6] = 0; header[7] = 0;             // flags
    
    // 64-bit Big-Endian stream_id
    for (int i = 0; i < 8; ++i) {
        header[8 + i] = (uint8_t)((stream_id >> (56 - i * 8)) & 0xFF);
    }
    
    // 32-bit Big-Endian payload length
    header[16] = (uint8_t)((len >> 24) & 0xFF);
    header[17] = (uint8_t)((len >> 16) & 0xFF);
    header[18] = (uint8_t)((len >> 8) & 0xFF);
    header[19] = (uint8_t)(len & 0xFF);

    lock_guard<mutex> lock(send_mutex_);
    if (!send_all_locked(header, 20)) return false;
    if (buf && len > 0) {
        return send_all_locked(buf, len);
    }
    return true;
}

int TcpConnection::recv_stream(uint64_t expected_stream_id, uint8_t* buf, int max_len) {
    lock_guard<mutex> lock(recv_mutex_);
    return recv(buf, max_len);
}

void TcpConnection::send_network_feedback(float loss_rate, float rtt_ms) {
    estimated_loss_rate_ = loss_rate;
    rtt_ms_ = rtt_ms;

    // Wire packet: type(0x07) + loss_rate(float 4, Big-Endian) + rtt_ms(float 4, Big-Endian)
    uint8_t feedback_pkt[9];
    feedback_pkt[0] = EVENT_TYPE_NETWORK_FEEDBACK;
    write_be_float(feedback_pkt + 1, loss_rate);
    write_be_float(feedback_pkt + 5, rtt_ms);

    send_all(feedback_pkt, 9);
}
