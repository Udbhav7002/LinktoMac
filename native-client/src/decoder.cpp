#include "decoder.h"
#include <iostream>
#include <cstring>

Decoder::Decoder() : 
    codec_(nullptr), codec_ctx_(nullptr), parser_(nullptr), 
    pkt_(nullptr), frame_(nullptr), sw_frame_(nullptr), 
    hw_device_ctx_(nullptr), hw_frames_ctx_(nullptr) {}

Decoder::~Decoder() {
    destroy();
}

bool Decoder::init_hw_frame_pool(int width, int height) {
    if (!hw_device_ctx_) return false;
    
    hw_frames_ctx_ = av_hwframe_ctx_alloc(hw_device_ctx_);
    if (!hw_frames_ctx_) return false;
    
    AVHWFramesContext* frames_ctx = (AVHWFramesContext*)hw_frames_ctx_->data;
    frames_ctx->format = AV_PIX_FMT_VIDEOTOOLBOX;
    frames_ctx->sw_format = AV_PIX_FMT_NV12;
    frames_ctx->width = width;
    frames_ctx->height = height;
    frames_ctx->initial_pool_size = 8; // 8 frames = ~133ms buffer @ 60fps
    
    if (av_hwframe_ctx_init(hw_frames_ctx_) < 0) {
        av_buffer_unref(&hw_frames_ctx_);
        return false;
    }
    
    codec_ctx_->hw_frames_ctx = av_buffer_ref(hw_frames_ctx_);
    return codec_ctx_->hw_frames_ctx != nullptr;
}

AVFrame* Decoder::acquire_sw_frame(int width, int height) {
    std::lock_guard<std::mutex> lock(pool_mutex_);
    
    if (!free_sw_frames_.empty()) {
        AVFrame* frame = free_sw_frames_.front();
        free_sw_frames_.pop();
        // Verify dimensions match (handle resolution changes)
        if (frame->width == width && frame->height == height && frame->format == AV_PIX_FMT_NV12) {
            return frame;
        }
        // Dimensions changed, free and allocate new
        av_frame_free(&frame);
    }
    
    // Allocate new frame
    AVFrame* frame = av_frame_alloc();
    if (!frame) return nullptr;
    
    frame->format = AV_PIX_FMT_NV12;
    frame->width = width;
    frame->height = height;
    if (av_frame_get_buffer(frame, 32) < 0) {
        av_frame_free(&frame);
        return nullptr;
    }
    return frame;
}

void Decoder::release_sw_frame(AVFrame* frame) {
    if (!frame) return;
    av_frame_unref(frame);
    std::lock_guard<std::mutex> lock(pool_mutex_);
    free_sw_frames_.push(frame);
}

bool Decoder::init() {
    std::lock_guard<std::mutex> lock(decode_mutex_);
    
    // Clean up any previous state (re-init safety)
    if (parser_) { av_parser_close(parser_); parser_ = nullptr; }
    if (codec_ctx_) { avcodec_free_context(&codec_ctx_); codec_ctx_ = nullptr; }
    if (pkt_) { av_packet_free(&pkt_); pkt_ = nullptr; }
    if (frame_) { av_frame_free(&frame_); frame_ = nullptr; }
    if (sw_frame_) { av_frame_free(&sw_frame_); sw_frame_ = nullptr; }
    if (hw_frames_ctx_) { av_buffer_unref(&hw_frames_ctx_); hw_frames_ctx_ = nullptr; }
    if (hw_device_ctx_) { av_buffer_unref(&hw_device_ctx_); hw_device_ctx_ = nullptr; }
    
    // Clear frame pool
    {
        std::lock_guard<std::mutex> pool_lock(pool_mutex_);
        while (!free_sw_frames_.empty()) {
            av_frame_free(&free_sw_frames_.front());
            free_sw_frames_.pop();
        }
    }
    
    // Try HEVC first (better compression), fall back to H.264
    codec_ = avcodec_find_decoder(AV_CODEC_ID_HEVC);
    if (!codec_) {
        std::cerr << "HEVC decoder not found, trying H.264\n";
        codec_ = avcodec_find_decoder(AV_CODEC_ID_H264);
    }
    if (!codec_) {
        std::cerr << "No HEVC/H.264 decoder found\n";
        return false;
    }

    codec_ctx_ = avcodec_alloc_context3(codec_);
    if (!codec_ctx_) return false;

    // Low latency settings
    codec_ctx_->flags |= AV_CODEC_FLAG_LOW_DELAY;
    codec_ctx_->flags2 |= AV_CODEC_FLAG2_FAST;

    // Try VideoToolbox hardware acceleration
    bool hw_enabled = false;
    if (av_hwdevice_ctx_create(&hw_device_ctx_, AV_HWDEVICE_TYPE_VIDEOTOOLBOX, nullptr, nullptr, 0) >= 0) {
        codec_ctx_->hw_device_ctx = av_buffer_ref(hw_device_ctx_);
        codec_ctx_->thread_count = 1; // HW decode doesn't benefit from threading
        hw_enabled = true;
        std::cout << "Using VideoToolbox hardware decoder\n";
    } else {
        std::cout << "VideoToolbox not available, using optimized software decoder\n";
        codec_ctx_->thread_count = 4;
        codec_ctx_->thread_type = FF_THREAD_SLICE;
    }

    if (avcodec_open2(codec_ctx_, codec_, nullptr) < 0) {
        std::cerr << "Failed to open codec\n";
        return false;
    }

    parser_ = av_parser_init(codec_->id);
    if (!parser_) return false;

    pkt_ = av_packet_alloc();
    frame_ = av_frame_alloc();
    sw_frame_ = av_frame_alloc(); // Legacy single frame for non-pooled path

    return true;
}

void Decoder::destroy() {
    std::lock_guard<std::mutex> lock(decode_mutex_);
    if (parser_) { av_parser_close(parser_); parser_ = nullptr; }
    if (codec_ctx_) { avcodec_free_context(&codec_ctx_); }
    if (pkt_) { av_packet_free(&pkt_); }
    if (frame_) { av_frame_free(&frame_); }
    if (sw_frame_) { av_frame_free(&sw_frame_); }
    if (hw_frames_ctx_) { av_buffer_unref(&hw_frames_ctx_); }
    if (hw_device_ctx_) { av_buffer_unref(&hw_device_ctx_); }
    
    // Free frame pool
    std::lock_guard<std::mutex> pool_lock(pool_mutex_);
    while (!free_sw_frames_.empty()) {
        av_frame_free(&free_sw_frames_.front());
        free_sw_frames_.pop();
    }
}

void Decoder::decode(const uint8_t* data, int size, FrameCallback callback) {
    std::lock_guard<std::mutex> lock(decode_mutex_);
    
    int data_size = size;
    uint8_t* data_ptr = const_cast<uint8_t*>(data);

    while (data_size > 0) {
        int consumed = av_parser_parse2(
            parser_, codec_ctx_,
            &pkt_->data, &pkt_->size,
            data_ptr, data_size,
            AV_NOPTS_VALUE, AV_NOPTS_VALUE, 0
        );

        if (consumed < 0) {
            std::cerr << "Error parsing bitstream\n";
            // Reset parser on parse error to prevent corruption
            av_parser_close(parser_);
            parser_ = av_parser_init(codec_->id);
            if (!parser_) return;
            return;
        }

        data_ptr += consumed;
        data_size -= consumed;

        if (pkt_->size > 0) {
            int ret = avcodec_send_packet(codec_ctx_, pkt_);
            if (ret < 0) {
                std::cerr << "Error sending a packet for decoding\n";
                continue;
            }

            while (ret >= 0) {
                ret = avcodec_receive_frame(codec_ctx_, frame_);
                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                    break;
                } else if (ret < 0) {
                    std::cerr << "Error during decoding\n";
                    break;
                }

                // If hardware accelerated, transfer data to CPU memory using pooled frames
                if (frame_->format == AV_PIX_FMT_VIDEOTOOLBOX) {
                    // Initialize frame pool on first hardware frame (we now know dimensions)
                    if (!hw_frames_ctx_ && frame_->width > 0 && frame_->height > 0) {
                        init_hw_frame_pool(frame_->width, frame_->height);
                    }
                    
                    AVFrame* sw_frame = acquire_sw_frame(frame_->width, frame_->height);
                    if (!sw_frame) {
                        std::cerr << "Failed to acquire software frame from pool\n";
                        continue;
                    }
                    
                    int hw_ret = av_hwframe_transfer_data(sw_frame, frame_, 0);
                    if (hw_ret == AVERROR(EAGAIN)) {
                        // VideoToolbox buffer pool exhausted - drain decoder and retry
                        AVFrame* drain_frame = av_frame_alloc();
                        while (avcodec_receive_frame(codec_ctx_, drain_frame) >= 0) {
                            av_frame_unref(drain_frame);
                        }
                        av_frame_free(&drain_frame);
                        // Retry the transfer
                        hw_ret = av_hwframe_transfer_data(sw_frame, frame_, 0);
                    }
                    if (hw_ret < 0) {
                        std::cerr << "Error transferring data to system memory: " << hw_ret << "\n";
                        release_sw_frame(sw_frame);
                        continue;
                    }
                    callback(sw_frame);
                    // Frame will be returned to pool via release_sw_frame in callback wrapper
                    // We need to track this - let's use a custom deleter approach
                    // For now, we'll release immediately after callback (caller must copy if needed)
                    release_sw_frame(sw_frame);
                } else {
                    callback(frame_);
                }
            }
        }
    }
}
