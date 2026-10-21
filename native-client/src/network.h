#pragma once

#include <string>
#include <stdint.h>
#include <mutex>
#include <vector>
#include <queue>
#include <chrono>
#include <openssl/ssl.h>
#include <openssl/err.h>

using namespace std;

// Multiplexed QUIC Stream IDs
enum QuicStreamType : uint64_t {
    QUIC_STREAM_CONTROL   = 0, // Bidirectional: Handshake, IDR sync, Feedback
    QUIC_STREAM_VIDEO     = 1, // Server -> Client: Video NAL units
    QUIC_STREAM_INPUT     = 2, // Client -> Server: Touch, Keys, Drag
    QUIC_STREAM_TELEMETRY = 3  // Server -> Client: Bitrate & Loss stats
};

struct QuicPacketHeader {
    uint32_t magic;      // 0x51554943 ("QUIC")
    uint32_t flags;      // 0x01: 0-RTT Resumption
    uint64_t stream_id;  // Multiplexed Stream ID
    uint32_t payload_len;// Byte length of chunk
};

class TcpConnection {
public:
    TcpConnection();
    ~TcpConnection();

    bool listen_and_accept(int port);
    bool connect(const string& host, int port);

    /**
     * Connects to a remote host and establishes a TLS 1.3 encrypted session.
     *
     * @param host Target hostname or IP address.
     * @param port Target TCP port.
     * @param ca_cert_path Optional filesystem path to trusted CA certificate for verification.
     * @return True if socket connection and TLS 1.3 handshake succeed.
     */
    bool connect_tls(const string& host, int port, const string& ca_cert_path = "");

    /**
     * Listens on a TCP port and wraps inbound accepted connection in TLS 1.3.
     *
     * @param port Listen TCP port.
     * @param cert_path Path to server PEM certificate.
     * @param key_path Path to server private key.
     * @param ca_cert_path Optional path to client CA certificate.
     * @return True if connection accepted and TLS 1.3 handshake succeeds.
     */
    bool listen_and_accept_tls(int port, const string& cert_path, const string& key_path, const string& ca_cert_path = "");

    void disconnect();
    void stop_listening();
    
    // Blocking read of exact bytes
    bool recv_exact(uint8_t* buf, int len);
    
    // Non-blocking read, returns bytes read or < 0 on error
    int recv(uint8_t* buf, int len);
    
    // Blocking write of all bytes
    bool send_all(const uint8_t* buf, int len);

    // Multiplexed stream transmission (eliminates head-of-line blocking)
    bool send_stream(uint64_t stream_id, const uint8_t* buf, int len);
    int recv_stream(uint64_t expected_stream_id, uint8_t* buf, int max_len);

    // 0-RTT Resumption Token management
    bool has_0rtt_token() const { return has_resumption_token_; }
    void store_0rtt_token(const vector<uint8_t>& token);
    const vector<uint8_t>& get_0rtt_token() const { return resumption_token_; }

    // Telemetry and feedback for dynamic bitrate adaptation
    float get_estimated_loss_rate() const { return estimated_loss_rate_; }
    float get_rtt_ms() const { return rtt_ms_; }
    void send_network_feedback(float loss_rate, float rtt_ms);

private:
    void apply_socket_options();
    void load_saved_0rtt_token();
    void persist_0rtt_token();
    bool send_all_locked(const uint8_t* buf, int len);
    void cleanup_tls();

    int sock_fd_;
    int server_fd_ = -1;
    mutex send_mutex_;
    mutex recv_mutex_;

    SSL_CTX* ssl_ctx_ = nullptr;
    SSL* ssl_ = nullptr;
    bool use_tls_ = false;

    bool has_resumption_token_{false};
    vector<uint8_t> resumption_token_;

    float estimated_loss_rate_{0.0f};
    float rtt_ms_{0.0f};
    chrono::steady_clock::time_point last_ping_time_{};
};
