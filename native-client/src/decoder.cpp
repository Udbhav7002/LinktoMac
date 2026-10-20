#include "decoder.h"
#include <iostream>
#include <cstring>

#ifdef __APPLE__
#include <CoreVideo/CoreVideo.h>
#endif

using namespace std;

Decoder::Decoder() :
    codec_(nullptr), codec_ctx_(nullptr), parser_(nullptr),
    pkt_(nullptr), frame_(nullptr), sw_frame_(nullptr),
    hw_device_ctx_(nullptr), hw_frames_ctx_(nullptr),
    pool_width_(0), pool_height_(0) {}

Decoder::~Decoder() {
    destroy();
}

bool Decoder::init_hw_frame_pool(int width, int height) {
    if (!codec_ctx_) return false;

    if (hw_device_ctx_) {
        if (hw_frames_ctx_) {
            av_buffer_unref(&hw_frames_ctx_);
            hw_frames_ctx_ = nullptr;
        }

        hw_frames_ctx_ = av_hwframe_ctx_alloc(hw_device_ctx_);
        if (hw_frames_ctx_) {
            AVHWFramesContext* frames_ctx = (AVHWFramesContext*)hw_frames_ctx_->data;
            frames_ctx->format = AV_PIX_FMT_VIDEOTOOLBOX;
            frames_ctx->sw_format = AV_PIX_FMT_NV12;
            frames_ctx->width = width;
            frames_ctx->height = height;
            frames_ctx->initial_pool_size = 8; // 8 frames pool for low-latency pipeline

            if (av_hwframe_ctx_init(hw_frames_ctx_) < 0) {
                av_buffer_unref(&hw_frames_ctx_);
                hw_frames_ctx_ = nullptr;
            }
        }

        // Note: Do NOT attach fixed hw_frames_ctx to codec_ctx_->hw_frames_ctx for VideoToolbox;
        // CoreVideo manages dynamic CVPixelBuffer allocation natively across resolution flips.
        if (codec_ctx_->hw_frames_ctx) {
            av_buffer_unref(&codec_ctx_->hw_frames_ctx);
            codec_ctx_->hw_frames_ctx = nullptr;
        }
    }

    // Pre-allocate recycled NV12 software frames to eliminate per-frame malloc overhead
    lock_guard<mutex> lock(pool_mutex_);
    while (!free_sw_frames_.empty()) {
        AVFrame* f = free_sw_frames_.front();
        free_sw_frames_.pop();
        av_frame_free(&f);
    }

    for (int i = 0; i < 8; ++i) {
        AVFrame* f = av_frame_alloc();
        if (f) {
            f->format = AV_PIX_FMT_NV12;
            f->width = width;
            f->height = height;
            if (av_frame_get_buffer(f, 32) >= 0) {
                free_sw_frames_.push(f);
            } else {
                av_frame_free(&f);
            }
        }
    }

    pool_width_ = width;
    pool_height_ = height;

    return true;
}

AVFrame* Decoder::acquire_sw_frame(int width, int height) {
    lock_guard<mutex> lock(pool_mutex_);

    while (!free_sw_frames_.empty()) {
        AVFrame* frame = free_sw_frames_.front();
        free_sw_frames_.pop();
        if (frame->width == width && frame->height == height && frame->format == AV_PIX_FMT_NV12) {
            return frame;
        }
        av_frame_free(&frame);
    }

    // Allocate new frame if pool is empty or resized
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
    lock_guard<mutex> lock(pool_mutex_);
    if (pool_width_ > 0 && pool_height_ > 0 &&
        (frame->width != pool_width_ || frame->height != pool_height_)) {
        av_frame_free(&frame);
        return;
    }
    free_sw_frames_.push(frame);
}

Decoder::FramePtr Decoder::make_frame_ptr(AVFrame* frame) {
    // Custom deleter returns frame to the recycled pool when render completes
    return FramePtr(frame, [this](AVFrame* f) {
        release_sw_frame(f);
    });
}

bool Decoder::init(uint32_t flags) {
    lock_guard<mutex> lock(decode_mutex_);
    AVCodecID preferred_codec = (flags & 0x01) ? AV_CODEC_ID_H264 : AV_CODEC_ID_HEVC;
    return reinit_locked(preferred_codec);
}

bool Decoder::reinit_locked(AVCodecID codec_id) {
    if (parser_) { av_parser_close(parser_); parser_ = nullptr; }
    if (codec_ctx_) { avcodec_free_context(&codec_ctx_); codec_ctx_ = nullptr; }
    if (pkt_) { av_packet_free(&pkt_); pkt_ = nullptr; }
    if (frame_) { av_frame_free(&frame_); frame_ = nullptr; }
    if (sw_frame_) { av_frame_free(&sw_frame_); sw_frame_ = nullptr; }
    if (hw_frames_ctx_) { av_buffer_unref(&hw_frames_ctx_); hw_frames_ctx_ = nullptr; }
    if (hw_device_ctx_) { av_buffer_unref(&hw_device_ctx_); hw_device_ctx_ = nullptr; }

    {
        lock_guard<mutex> pool_lock(pool_mutex_);
        while (!free_sw_frames_.empty()) {
            AVFrame* f = free_sw_frames_.front();
            free_sw_frames_.pop();
            av_frame_free(&f);
        }
    }

    // Reset pool dimensions on reinit
    pool_width_ = 0;
    pool_height_ = 0;

    codec_ = avcodec_find_decoder(codec_id);
    if (!codec_) {
        AVCodecID fallback = (codec_id == AV_CODEC_ID_HEVC) ? AV_CODEC_ID_H264 : AV_CODEC_ID_HEVC;
        cerr << "[WARN] Preferred decoder not found, trying fallback\n";
        codec_ = avcodec_find_decoder(fallback);
    }
    if (!codec_) {
        cerr << "[ERROR] No HEVC/H.264 decoder found\n";
        return false;
    }

    cout << "[INFO] Initialized decoder: " << codec_->name << "\n";

    codec_ctx_ = avcodec_alloc_context3(codec_);
    if (!codec_ctx_) return false;

    // Apple Silicon / low latency flags
    codec_ctx_->flags |= AV_CODEC_FLAG_LOW_DELAY;
    codec_ctx_->flags2 |= AV_CODEC_FLAG2_FAST;

    // Enable VideoToolbox hardware acceleration with zero-copy pipeline
    if (av_hwdevice_ctx_create(&hw_device_ctx_, AV_HWDEVICE_TYPE_VIDEOTOOLBOX, nullptr, nullptr, 0) >= 0) {
        codec_ctx_->hw_device_ctx = av_buffer_ref(hw_device_ctx_);
        codec_ctx_->thread_count = 1; // VideoToolbox handles parallelism in hardware
        cout << "[INFO] Enabled VideoToolbox hardware decoder for Apple Silicon / macOS\n";
    } else {
        cout << "[INFO] VideoToolbox not available, using software decoder for " << codec_->name << "\n";
        codec_ctx_->thread_count = 4;
        codec_ctx_->thread_type = FF_THREAD_SLICE;
    }

    if (avcodec_open2(codec_ctx_, codec_, nullptr) < 0) {
        cerr << "[ERROR] Failed to open codec\n";
        return false;
    }

    parser_ = av_parser_init(codec_->id);
    if (!parser_) return false;

    pkt_ = av_packet_alloc();
    frame_ = av_frame_alloc();
    sw_frame_ = av_frame_alloc();

    has_decoded_frame_ = false;
    return true;
}

void Decoder::destroy() {
    lock_guard<mutex> lock(decode_mutex_);
    if (parser_) { av_parser_close(parser_); parser_ = nullptr; }
    if (codec_ctx_) { avcodec_free_context(&codec_ctx_); codec_ctx_ = nullptr; }
    if (pkt_) { av_packet_free(&pkt_); pkt_ = nullptr; }
    if (frame_) { av_frame_free(&frame_); frame_ = nullptr; }
    if (sw_frame_) { av_frame_free(&sw_frame_); sw_frame_ = nullptr; }
    if (hw_frames_ctx_) { av_buffer_unref(&hw_frames_ctx_); hw_frames_ctx_ = nullptr; }
    if (hw_device_ctx_) { av_buffer_unref(&hw_device_ctx_); hw_device_ctx_ = nullptr; }

    lock_guard<mutex> pool_lock(pool_mutex_);
    while (!free_sw_frames_.empty()) {
        AVFrame* f = free_sw_frames_.front();
        free_sw_frames_.pop();
        av_frame_free(&f);
    }

    pool_width_ = 0;
    pool_height_ = 0;
}

void Decoder::decode(const uint8_t* data, int size, FrameCallback callback) {
    lock_guard<mutex> lock(decode_mutex_);
    if (!data || size <= 0) return;
    if (!codec_ || !codec_ctx_ || !parser_ || !pkt_ || !frame_) return;

    // Only auto-detect codec if decoder was not explicitly initialized via handshake
    if (!codec_ && size >= 5) {
        for (int i = 0; i <= size - 5; ++i) {
            if (data[i] == 0 && data[i+1] == 0 && (data[i+2] == 1 || (data[i+2] == 0 && data[i+3] == 1))) {
                int nal_idx = (data[i+2] == 1) ? i + 3 : i + 4;
                uint8_t h = data[nal_idx];
                uint8_t h264_type = h & 0x1F;
                uint8_t hevc_type = (h >> 1) & 0x3F;
                if ((h & 0x80) == 0 && (h264_type == 7 || h264_type == 8)) {
                    cout << "[INFO] Detected H.264 SPS/PPS NAL in bitstream, initializing decoder\n";
                    reinit_locked(AV_CODEC_ID_H264);
                    break;
                } else if (nal_idx + 1 < size) {
                    bool hevc_header_valid = ((data[nal_idx] & 0x80) == 0) && (((data[nal_idx+1] >> 3) & 0x3F) == 0);
                    if (hevc_header_valid && (hevc_type == 32 || hevc_type == 33 || hevc_type == 34)) {
                        cout << "[INFO] Detected HEVC VPS/SPS NAL in bitstream, initializing decoder\n";
                        reinit_locked(AV_CODEC_ID_HEVC);
                        break;
                    }
                }
            }
        }
    }

    if (!codec_ || !codec_ctx_ || !parser_ || !pkt_ || !frame_) return;

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
            cerr << "[ERROR] Error parsing bitstream\n";
            if (error_callback_) error_callback_();
            av_parser_close(parser_);
            parser_ = codec_ ? av_parser_init(codec_->id) : nullptr;
            if (!parser_) return;
            return;
        }

        data_ptr += consumed;
        data_size -= consumed;

        if (pkt_->size > 0) {
            // Detect dynamic resolution / orientation flip from bitstream SPS before packet submission
            if (parser_->width > 0 && parser_->height > 0 &&
                (parser_->width != pool_width_ || parser_->height != pool_height_)) {
                if (has_decoded_frame_) {
                    cout << "[INFO] Mid-stream orientation change detected: "
                         << pool_width_ << "x" << pool_height_ << " -> "
                         << parser_->width << "x" << parser_->height << ", flushing decoder\n";
                    avcodec_flush_buffers(codec_ctx_);
                }
                init_hw_frame_pool(parser_->width, parser_->height);
            }

            int ret = avcodec_send_packet(codec_ctx_, pkt_);
            if (ret < 0) {
                cerr << "[ERROR] Error sending packet to decoder\n";
                if (error_callback_) error_callback_();
                continue;
            }

            while (ret >= 0) {
                ret = avcodec_receive_frame(codec_ctx_, frame_);
                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                    break;
                } else if (ret < 0) {
                    cerr << "[ERROR] Error during decoding\n";
                    if (error_callback_) error_callback_();
                    break;
                }

                // VideoToolbox hardware decode path
                if (frame_->format == AV_PIX_FMT_VIDEOTOOLBOX) {
                    AVFrame* pooled_sw_frame = acquire_sw_frame(frame_->width, frame_->height);
                    if (!pooled_sw_frame) {
                        cerr << "[WARN] Frame pool empty, dropping frame\n";
                        continue;
                    }

                    bool copied = false;
#ifdef __APPLE__
                    // Apple Silicon unified memory optimization: direct CVPixelBuffer plane access
                    // Eliminates av_hwframe_transfer_data overhead and internal mallocs
                    CVPixelBufferRef pixbuf = (CVPixelBufferRef)frame_->data[3];
                    if (pixbuf && CVPixelBufferLockBaseAddress(pixbuf, kCVPixelBufferLock_ReadOnly) == kCVReturnSuccess) {
                        uint8_t* y_src = (uint8_t*)CVPixelBufferGetBaseAddressOfPlane(pixbuf, 0);
                        size_t y_stride_src = CVPixelBufferGetBytesPerRowOfPlane(pixbuf, 0);
                        uint8_t* uv_src = (uint8_t*)CVPixelBufferGetBaseAddressOfPlane(pixbuf, 1);
                        size_t uv_stride_src = CVPixelBufferGetBytesPerRowOfPlane(pixbuf, 1);

                        int w = frame_->width;
                        int h = frame_->height;

                        // Fast row copy into pre-allocated pooled frame
                        uint8_t* y_dst = pooled_sw_frame->data[0];
                        int y_stride_dst = pooled_sw_frame->linesize[0];
                        for (int row = 0; row < h; ++row) {
                            memcpy(y_dst + row * y_stride_dst, y_src + row * y_stride_src, w);
                        }

                        uint8_t* uv_dst = pooled_sw_frame->data[1];
                        int uv_stride_dst = pooled_sw_frame->linesize[1];
                        int uv_h = h / 2;
                        for (int row = 0; row < uv_h; ++row) {
                            memcpy(uv_dst + row * uv_stride_dst, uv_src + row * uv_stride_src, w);
                        }

                        CVPixelBufferUnlockBaseAddress(pixbuf, kCVPixelBufferLock_ReadOnly);
                        copied = true;
                    }
#endif
                    if (!copied) {
                        // Fallback to pooled hwframe transfer
                        int hw_ret = av_hwframe_transfer_data(pooled_sw_frame, frame_, 0);
                        if (hw_ret < 0) {
                            cerr << "[ERROR] Transfer from VideoToolbox failed: " << hw_ret << "\n";
                            release_sw_frame(pooled_sw_frame);
                            continue;
                        }
                    }

                    pooled_sw_frame->width = frame_->width;
                    pooled_sw_frame->height = frame_->height;
                    has_decoded_frame_ = true;

                    FramePtr frame_ptr = make_frame_ptr(pooled_sw_frame);
                    callback(frame_ptr);
                } else {
                    // Software decode path
                    AVFrame* cloned = av_frame_clone(frame_);
                    if (cloned) {
                        has_decoded_frame_ = true;
                        FramePtr frame_ptr(cloned, [](AVFrame* f) { av_frame_free(&f); });
                        callback(frame_ptr);
                    }
                }
            }
        }
    }
}
