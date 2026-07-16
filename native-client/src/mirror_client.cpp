#include "mirror_client.h"
#include "protocol.h"
#include <iostream>
#include <thread>
#include <chrono>
#include <condition_variable>

MirrorClient::MirrorClient(const std::string& host, int video_port)
    : host_(host), video_port_(video_port), input_handler_(nullptr) {}

MirrorClient::~MirrorClient() {
    stop();
}

bool MirrorClient::start() {
    if (running_) return false;
    running_ = true;

    bool connected = false;
    int width = 0, height = 0, flags = 0;
    uint32_t protocol_version = 1; // Current protocol version

    if (connect_mode_) {
        // Client mode: connect to the Android server (used with ADB forwarding)
        std::cout << "[INFO] Connecting to " << host_ << ":" << video_port_ << "..." << std::endl;
        if (!video_sock_.connect(host_, video_port_)) {
            std::cerr << "[ERROR] Failed to connect to " << host_ << ":" << video_port_ << std::endl;
            return false;
        }
        // In connect mode, we are the client — the server sends us the handshake
        uint8_t handshake[20]; // Extended handshake: magic(4) + version(4) + width(4) + height(4) + flags(4)
        if (!video_sock_.recv_exact(handshake, 20)) {
            // Try legacy 16-byte handshake
            if (!video_sock_.recv_exact(handshake, 16)) {
                std::cerr << "[ERROR] Failed to read handshake from server" << std::endl;
                return false;
            }
            // Legacy handshake: assume version 1
            protocol_version = 1;
        } else {
            uint32_t magic = read_be32(handshake);
            if (magic != 0x4C494E4B) {
                std::cerr << "[ERROR] Invalid magic number from server (0x" << std::hex << magic << std::dec << ")" << std::endl;
                return false;
            }
            protocol_version = read_be32(handshake + 4);
        }
        width = read_be32(handshake + 8);
        height = read_be32(handshake + 12);
        flags = read_be32(handshake + 16);
        if (width <= 0 || width > 7680 || height <= 0 || height > 7680) {
            std::cerr << "[ERROR] Invalid dimensions: " << width << "x" << height << std::endl;
            return false;
        }
        connected = true;
    } else {
        // Server mode: listen for incoming connection (used with Pinggy remote support)
        int max_retries = 1000000;
        for (int attempt = 1; attempt <= max_retries && running_; ++attempt) {
            video_sock_.disconnect();

            std::cout << "[INFO] Waiting for incoming connection (attempt " << attempt << ")..." << std::endl;
            if (!video_sock_.listen_and_accept(video_port_)) {
                std::cerr << "[ERROR] Failed to accept connection, retrying in 1s..." << std::endl;
                std::this_thread::sleep_for(std::chrono::seconds(1));
                continue;
            }

            uint8_t handshake[20];
            if (!video_sock_.recv_exact(handshake, 20)) {
                // Try legacy 16-byte handshake
                if (!video_sock_.recv_exact(handshake, 16)) {
                    std::cerr << "[ERROR] Failed to read handshake, ignoring..." << std::endl;
                    continue;
                }
                protocol_version = 1;
            } else {
                uint32_t magic = read_be32(handshake);
                if (magic != 0x4C494E4B) {
                    std::cerr << "[ERROR] Invalid magic number (0x" << std::hex << magic << std::dec << "), ignoring." << std::endl;
                    continue;
                }
                protocol_version = read_be32(handshake + 4);
            }
            width = read_be32(handshake + 8);
            height = read_be32(handshake + 12);
            flags = read_be32(handshake + 16);
            
            if (width <= 0 || width > 7680 || height <= 0 || height > 7680) {
                std::cerr << "[ERROR] Invalid dimensions: " << width << "x" << height << ". Ignoring." << std::endl;
                continue;
            }
            
            connected = true;
            break;
        }
    }

    if (!connected) {
        std::cerr << "[ERROR] Failed to establish a valid connection" << std::endl;
        return false;
    }
    
    std::cout << "[INFO] Device connected: " << width << "x" << height << std::endl;

    if (!decoder_.init()) return false;
    if (!renderer_.init(width, height, "LinktoMac")) return false;

    // Use the SAME socket for both reading video and writing input (Multiplexing)
    input_handler_ = new InputHandler(&video_sock_, &renderer_);

    running_ = true;
    decode_thread_ = std::thread(&MirrorClient::decode_thread_func, this);

    // Main event/render loop (Must run on main thread on macOS for SDL)
    while (running_) {
        if (!input_handler_->process_events()) {
            running_ = false;
            break;
        }

        AVFrame* frame_to_render = nullptr;
        {
            std::unique_lock<std::mutex> lock(queue_mutex_);
            if (!frame_queue_.empty()) {
                // Keep the most recent frame, discard older ones if we are lagging behind
                while (frame_queue_.size() > 1) {
                    av_frame_free(&frame_queue_.front());
                    frame_queue_.pop();
                }
                frame_to_render = frame_queue_.front();
                frame_queue_.pop();
            }
        }

        if (frame_to_render) {
            renderer_.render_frame(frame_to_render);
            av_frame_free(&frame_to_render);
        } else {
            // Small sleep to prevent burning CPU if no frames are ready
            SDL_Delay(5);
        }
    }

    stop();
    return true;
}

void MirrorClient::stop() {
    // Signal shutdown first
    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        running_ = false;
        shutdown_requested_ = true;
        queue_cv_.notify_all();
    }

    // Wait for decode thread to finish
    if (decode_thread_.joinable()) {
        if (std::this_thread::get_id() != decode_thread_.get_id()) {
            decode_thread_.join();
        } else {
            decode_thread_.detach();
        }
    }

    video_sock_.disconnect();
    video_sock_.stop_listening();

    // Clean up frame queue (now thread is joined, no race)
    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        while (!frame_queue_.empty()) {
            av_frame_free(&frame_queue_.front());
            frame_queue_.pop();
        }
    }

    if (input_handler_) {
        delete input_handler_;
        input_handler_ = nullptr;
    }
    
    stopped_ = true;
}

void MirrorClient::decode_thread_func() {
    uint8_t buf[65536]; // 64KB read buffer

    auto frame_cb = [this](AVFrame* frame) {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        if (!running_ || shutdown_requested_) return;
        
        // Deep copy the frame to fully decouple it from the hardware decoder's buffer pool.
        AVFrame* clone = av_frame_alloc();
        if (!clone) return;
        clone->format = frame->format;
        clone->width = frame->width;
        clone->height = frame->height;
        
        if (av_frame_get_buffer(clone, 32) >= 0) {
            av_frame_copy(clone, frame);
            av_frame_copy_props(clone, frame);
            
            static int frame_count = 0;
            if (frame_count++ % 30 == 0) {
                std::cout << "[DEBUG] Decoded frame " << frame_count << " format=" << clone->format << " " << clone->width << "x" << clone->height << "\n";
            }
            
            // Prevent memory explosion if main thread is blocked
            while (frame_queue_.size() >= 2) {
                av_frame_free(&frame_queue_.front());
                frame_queue_.pop();
            }
            frame_queue_.push(clone);
            queue_cv_.notify_one();
        } else {
            av_frame_free(&clone);
        }
    };

    while (running_) {
        {
            std::lock_guard<std::mutex> lock(queue_mutex_);
            if (!running_ || shutdown_requested_) break;
        }
        
        int bytes = video_sock_.recv(buf, sizeof(buf));
        if (bytes <= 0) {
            std::cerr << "[ERROR] Video socket closed or error\n";
            break;
        }

        static int recv_count = 0;
        if (recv_count++ % 100 == 0) {
            std::cout << "[DEBUG] Received video chunk of " << bytes << " bytes\n";
        }
        
        decoder_.decode(buf, bytes, frame_cb);
    }
    
    // Signal completion
    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        shutdown_requested_ = true;
        queue_cv_.notify_all();
    }
}