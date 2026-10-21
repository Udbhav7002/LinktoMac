#include "mirror_client.h"
#include "protocol.h"
#include <iostream>
#include <thread>
#include <chrono>
#include <condition_variable>
#include <cmath>
#include <algorithm>

using namespace std;
using namespace chrono;

MirrorClient::MirrorClient(const string& host, int video_port)
    : host_(host), video_port_(video_port), input_handler_(nullptr) {}

MirrorClient::~MirrorClient() {
    stop();
}

bool MirrorClient::verify_peer_token() {
    if (auth_token_.empty()) return true;  // local-only fallback
    char buf[36] = {0};
    if (!video_sock_.recv_exact(reinterpret_cast<uint8_t*>(buf), 36)) return false;
    // Compare constant-time
    int diff = 0;
    for (int i = 0; i < 36; ++i) {
        char expected = (i < static_cast<int>(auth_token_.size())) ? auth_token_[i] : '\0';
        diff |= static_cast<int>(buf[i]) ^ static_cast<int>(expected);
    }
    return diff == 0;
}

bool MirrorClient::start() {
    if (running_) return false;
    running_ = true;
    stream_error_ = false;
    user_quit_ = false;
    shutdown_requested_ = false;
    stopped_ = false;

    bool connected = false;
    int width = 0, height = 0, flags = 0;
    uint32_t protocol_version = 1;

    if (connect_mode_) {
        // Client mode: connect to Android server (ADB forwarding / direct IP)
        cout << "[INFO] Connecting to " << host_ << ":" << video_port_ << "..." << endl;
        if (!video_sock_.connect(host_, video_port_)) {
            cerr << "[ERROR] Failed to connect to " << host_ << ":" << video_port_ << endl;
            stop();
            return false;
        }
        uint8_t handshake[HANDSHAKE_SIZE];
        if (!video_sock_.recv_exact(handshake, HANDSHAKE_SIZE)) {
            cerr << "[ERROR] Failed to read " << HANDSHAKE_SIZE << "-byte handshake from server" << endl;
            stop();
            return false;
        }

        uint32_t magic = read_be32(handshake);
        if (magic != HANDSHAKE_MAGIC) {
            cerr << "[ERROR] Invalid magic number from server (0x" << hex << magic << dec << ")" << endl;
            stop();
            return false;
        }

        protocol_version = read_be32(handshake + 4);
        width = read_be32(handshake + 8);
        height = read_be32(handshake + 12);
        flags = read_be32(handshake + 16);

        if (width <= 0 || width > 7680 || height <= 0 || height > 7680) {
            cerr << "[ERROR] Invalid dimensions: " << width << "x" << height << endl;
            stop();
            return false;
        }
        connected = true;
    } else {
        // Server mode: listen for incoming connection (remote session)
        int max_retries = 1000000;
        for (int attempt = 1; attempt <= max_retries && running_; ++attempt) {
            video_sock_.disconnect();

            cout << "[INFO] Waiting for incoming connection (attempt " << attempt << ")..." << endl;
            if (!video_sock_.listen_and_accept(video_port_)) {
                cerr << "[ERROR] Failed to accept connection, retrying in 1s..." << endl;
                this_thread::sleep_for(seconds(1));
                continue;
            }

            if (!verify_peer_token()) {
                cerr << "[ERROR] Auth token mismatch, closing connection" << endl;
                video_sock_.disconnect();
                continue;
            }

            uint8_t handshake[HANDSHAKE_SIZE];
            if (!video_sock_.recv_exact(handshake, HANDSHAKE_SIZE)) {
                cerr << "[ERROR] Failed to read " << HANDSHAKE_SIZE << "-byte handshake, ignoring..." << endl;
                continue;
            }

            uint32_t magic = read_be32(handshake);
            if (magic != HANDSHAKE_MAGIC) {
                cerr << "[ERROR] Invalid magic number (0x" << hex << magic << dec << "), ignoring." << endl;
                continue;
            }

            protocol_version = read_be32(handshake + 4);
            width = read_be32(handshake + 8);
            height = read_be32(handshake + 12);
            flags = read_be32(handshake + 16);

            if (width <= 0 || width > 7680 || height <= 0 || height > 7680) {
                cerr << "[ERROR] Invalid dimensions: " << width << "x" << height << ". Ignoring." << endl;
                continue;
            }

            connected = true;
            break;
        }
    }

    if (!connected) {
        cerr << "[ERROR] Failed to establish a valid connection" << endl;
        stop();
        return false;
    }

    cout << "[INFO] Device connected: " << width << "x" << height << " (protocol v" << protocol_version << ")" << endl;

    // Send on-demand keyframe sync immediately after handshake
    request_keyframe();

    if (!decoder_.init(flags)) {
        cerr << "[ERROR] Failed to initialize decoder" << endl;
        stop();
        return false;
    }
    if (!renderer_.init(width, height, "LinktoMac")) {
        cerr << "[ERROR] Failed to initialize renderer" << endl;
        stop();
        return false;
    }

    // Multiplexed channel for video and input
    input_handler_ = new InputHandler(&video_sock_, &renderer_);

    running_ = true;
    decode_thread_ = thread(&MirrorClient::decode_thread_func, this);

    // Main event/render loop on main thread (required by macOS Cocoa/Metal)
    while (running_) {
        if (shutdown_requested_) {
            running_ = false;
            break;
        }

        if (!input_handler_->process_events()) {
            user_quit_ = true;
            running_ = false;
            break;
        }

        Decoder::FramePtr frame_to_render;
        {
            unique_lock<mutex> lock(queue_mutex_);
            if (!frame_queue_.empty()) {
                // If queue has accumulated frames, drop all stale frames
                while (frame_queue_.size() > 1) {
                    frame_queue_.pop();
                    stats_.dropped_frames++;
                }
                frame_to_render = frame_queue_.front();
                frame_queue_.pop();
            }
        }

        if (frame_to_render) {
            auto render_start = steady_clock::now();
            renderer_.render_frame(frame_to_render.get());
            auto render_end = steady_clock::now();
            stats_.render_time_us = duration_cast<microseconds>(render_end - render_start).count();
            stats_.rendered_frames++;
        } else {
            SDL_Delay(5);
        }
    }

    stop();
    return !stream_error_;
}

void MirrorClient::stop() {
    {
        lock_guard<mutex> lock(queue_mutex_);
        running_ = false;
        shutdown_requested_ = true;
        queue_cv_.notify_all();
    }

    if (decode_thread_.joinable()) {
        if (this_thread::get_id() != decode_thread_.get_id()) {
            decode_thread_.join();
        } else {
            decode_thread_.detach();
        }
    }

    video_sock_.disconnect();
    video_sock_.stop_listening();

    {
        lock_guard<mutex> lock(queue_mutex_);
        while (!frame_queue_.empty()) {
            frame_queue_.pop();
        }
    }

    if (input_handler_) {
        delete input_handler_;
        input_handler_ = nullptr;
    }

    renderer_.destroy();
    stopped_ = true;
}

void MirrorClient::request_keyframe() {
    auto now = steady_clock::now();
    lock_guard<mutex> lock(sync_req_mutex_);
    if (duration_cast<milliseconds>(now - last_sync_request_).count() < 200) {
        return; // Suppress duplicate requests within 200ms
    }
    last_sync_request_ = now;

    uint8_t sync_cmd = EVENT_TYPE_SYNC_REQUEST;
    if (video_sock_.send_all(&sync_cmd, 1)) {
        cout << "[INFO] Emitted on-demand IDR sync request (0x05) to prevent visual artifacting" << endl;
    }
}

void MirrorClient::decode_thread_func() {
    uint8_t buf[65536]; // 64KB read buffer

    decoder_.set_error_callback([this]() {
        request_keyframe();
    });

    auto last_successful_frame_time = steady_clock::now();
    auto last_feedback_time = steady_clock::now();
    int window_frames_decoded = 0;
    int window_frames_dropped = 0;
    auto last_frame_arrival = steady_clock::time_point{};
    float smoothed_jitter_ms = 5.0f;

    auto frame_cb = [this, &last_successful_frame_time, &window_frames_decoded, &window_frames_dropped, &last_frame_arrival, &smoothed_jitter_ms](Decoder::FramePtr frame) {
        auto now = steady_clock::now();
        last_successful_frame_time = now;

        if (last_frame_arrival.time_since_epoch().count() == 0) {
            last_frame_arrival = now;
        } else {
            float dt_ms = duration_cast<microseconds>(now - last_frame_arrival).count() / 1000.0f;
            last_frame_arrival = now;
            float frame_jitter = std::abs(dt_ms - 16.67f);
            smoothed_jitter_ms = 0.9f * smoothed_jitter_ms + 0.1f * frame_jitter;
        }
        window_frames_decoded++;

        lock_guard<mutex> lock(queue_mutex_);
        if (!running_ || shutdown_requested_) return;

        // Drain stale frames to prevent lag accumulation
        while (frame_queue_.size() >= 2) {
            frame_queue_.pop();
            stats_.dropped_frames++;
            window_frames_dropped++;
        }
        frame_queue_.push(std::move(frame));
        queue_cv_.notify_one();
    };

    int consecutive_timeouts = 0;

    while (running_) {
        {
            lock_guard<mutex> lock(queue_mutex_);
            if (!running_ || shutdown_requested_) break;
        }

        int bytes = video_sock_.recv(buf, sizeof(buf));
        if (bytes < 0) {
            cerr << "[ERROR] Video transport closed or read error" << endl;
            if (!user_quit_ && !shutdown_requested_) {
                stream_error_ = true;
            }
            break;
        }
        if (bytes == 0) {
            // Socket read timeout (4s) on static screen - send 0x05 heartbeat to keep alive
            consecutive_timeouts++;
            if (consecutive_timeouts >= 2) {
                // 8s of dead silence despite 0x05 pings - treat as stream error
                if (!user_quit_) {
                    stream_error_ = true;
                }
                break;
            }
            request_keyframe();
            continue;
        }

        // Reset consecutive timeouts on any data received
        consecutive_timeouts = 0;

        // Measure decode time for WebRTC-style adaptive frame dropping
        auto decode_start = steady_clock::now();
        decoder_.decode(buf, bytes, frame_cb);
        auto decode_end = steady_clock::now();

        stats_.decode_time_us = duration_cast<microseconds>(decode_end - decode_start).count();

        // WebRTC-style frame-dropping: if decode time exceeds 16.67ms (target 60 FPS frame time),
        // aggressively drop stale frames from queue (but DON'T request IDR on decode jitter)
        if (stats_.decode_time_us > target_frame_time_us_) {
            lock_guard<mutex> lock(queue_mutex_);
            if (frame_queue_.size() > 1) {
                cout << "[WARN] Decode time (" << (stats_.decode_time_us / 1000.0) 
                     << "ms) exceeded 16.67ms limit. Dropping stale backlog." << endl;
                while (frame_queue_.size() > 1) {
                    frame_queue_.pop();
                    stats_.dropped_frames++;
                    window_frames_dropped++;
                }
            }
            // Removed: request_keyframe() - don't spam IDR on decode jitter
        }

        // Detect video stall: data received but no frame produced in > 1000ms
        auto now = steady_clock::now();
        if (duration_cast<milliseconds>(now - last_successful_frame_time).count() > 1000) {
            cout << "[WARN] Video stream stall detected (>1000ms), requesting IDR keyframe" << endl;
            request_keyframe();
            last_successful_frame_time = now;
        }

        // Periodic ABR telemetry feedback every 1000ms
        if (duration_cast<milliseconds>(now - last_feedback_time).count() >= 1000) {
            int total = window_frames_decoded + window_frames_dropped;
            float loss_rate = (total > 0) ? (static_cast<float>(window_frames_dropped) / static_cast<float>(total)) : 0.0f;
            float rtt_ms = std::max(5.0f, smoothed_jitter_ms);
            video_sock_.send_network_feedback(loss_rate, rtt_ms);
            last_feedback_time = now;
            window_frames_decoded = 0;
            window_frames_dropped = 0;
        }
    }

    {
        lock_guard<mutex> lock(queue_mutex_);
        shutdown_requested_ = true;
        running_ = false;
        queue_cv_.notify_all();
    }

    // Push SDL_QUIT event to immediately unblock main event loop and destroy window
    SDL_Event quit_event;
    quit_event.type = SDL_QUIT;
    SDL_PushEvent(&quit_event);
}
