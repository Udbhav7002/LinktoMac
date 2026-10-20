#pragma once

#include "network.h"
#include "decoder.h"
#include "renderer.h"
#include "input_handler.h"
#include <string>
#include <thread>
#include <atomic>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <chrono>

using namespace std;

struct FrameStats {
    int64_t decode_time_us = 0;
    int64_t render_time_us = 0;
    int dropped_frames = 0;
    int rendered_frames = 0;
};

class MirrorClient {
public:
    MirrorClient(const string& host, int video_port);
    ~MirrorClient();

    void set_connect_mode(bool connect) { connect_mode_ = connect; }

    bool start();
    void stop();
    bool is_running() const { return running_; }

    void request_keyframe();
    const FrameStats& get_stats() const { return stats_; }

private:
    void decode_thread_func();

    string host_;
    int video_port_;
    bool connect_mode_ = false;

    TcpConnection video_sock_;
    
    Decoder decoder_;
    Renderer renderer_;
    InputHandler* input_handler_;

    atomic<bool> running_{false};
    atomic<bool> stopped_{false};
    atomic<bool> shutdown_requested_{false};
    atomic<bool> stream_error_{false};
    atomic<bool> user_quit_{false};
    thread decode_thread_;

    mutex sync_req_mutex_;
    chrono::steady_clock::time_point last_sync_request_{};

    // Frame queue for passing decoded frames to renderer thread
    // Uses Decoder::FramePtr (shared_ptr with pool-returning deleter) for zero-copy pooling
    queue<Decoder::FramePtr> frame_queue_;
    mutex queue_mutex_;
    condition_variable queue_cv_;

    FrameStats stats_;
    int64_t target_frame_time_us_ = 16666; // 16.67ms (60 FPS)
};
