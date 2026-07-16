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

class MirrorClient {
public:
    MirrorClient(const std::string& host, int video_port);
    ~MirrorClient();

    void set_connect_mode(bool connect) { connect_mode_ = connect; }

    bool start();
    void stop();
    bool is_running() const { return running_; }

private:
    void decode_thread_func();

    std::string host_;
    int video_port_;
    bool connect_mode_ = false;

    TcpConnection video_sock_;
    
    Decoder decoder_;
    Renderer renderer_;
    InputHandler* input_handler_;

    std::atomic<bool> running_{false};
    std::atomic<bool> stopped_{false};
    std::atomic<bool> shutdown_requested_{false};
    std::thread decode_thread_;

    // Frame queue for passing decoded frames to renderer thread
    std::queue<AVFrame*> frame_queue_;
    std::mutex queue_mutex_;
    std::condition_variable queue_cv_;
};
