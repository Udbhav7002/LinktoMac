#pragma once

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/hwcontext.h>
#include <libavutil/hwcontext_videotoolbox.h>
}
#include <functional>
#include <memory>
#include <mutex>
#include <queue>

using namespace std;

class Decoder {
public:
    // FramePtr with custom deleter for returning frames to pool after rendering
    using FramePtr = shared_ptr<AVFrame>;
    using FrameCallback = function<void(FramePtr)>;
    using ErrorCallback = function<void()>;

    Decoder();
    ~Decoder();

    bool init(uint32_t flags = 0);
    void destroy();
    void set_error_callback(ErrorCallback callback) { error_callback_ = callback; }

    // Decodes raw video data (HEVC or H.264), calls callback for each fully decoded frame
    void decode(const uint8_t* data, int size, FrameCallback callback);

private:
    ErrorCallback error_callback_;
    const AVCodec* codec_;
    AVCodecContext* codec_ctx_;
    AVCodecParserContext* parser_;
    AVPacket* pkt_;
    AVFrame* frame_;
    AVFrame* sw_frame_; // Hardware-to-software transfer frame
    AVBufferRef* hw_device_ctx_;
    AVBufferRef* hw_frames_ctx_; // VideoToolbox hardware frame context pool

    // Hardware frame pool dimensions (for rotation detection)
    int pool_width_ = 0;
    int pool_height_ = 0;

    // Recycled software frames pool (eliminates per-frame malloc overhead)
    queue<AVFrame*> free_sw_frames_;
    mutex pool_mutex_;
    mutex decode_mutex_;
    bool has_decoded_frame_{false};

    bool reinit_locked(AVCodecID codec_id);
    bool init_hw_frame_pool(int width, int height);
    AVFrame* acquire_sw_frame(int width, int height);
    void release_sw_frame(AVFrame* frame);

    // Creates shared_ptr FramePtr with pool-returning deleter
    FramePtr make_frame_ptr(AVFrame* frame);
};
