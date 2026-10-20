#pragma once

#include <string>
#include <stdint.h>
#include <mutex>
#include <vector>
#include <queue>
#include <chrono>

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

    int sock_fd_;
    int server_fd_ = -1;
    mutex send_mutex_;
    mutex recv_mutex_;

    bool has_resumption_token_{false};
    vector<uint8_t> resumption_token_;

    float estimated_loss_rate_{0.0f};
    float rtt_ms_{0.0f};
    chrono::steady_clock::time_point last_ping_time_{};
};
