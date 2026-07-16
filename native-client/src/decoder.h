#pragma once

extern "C" {
#include <libavcodec/avcodec.h>
}
#include <functional>
#include <mutex>
#include <queue>

class Decoder {
public:
    using FrameCallback = std::function<void(AVFrame*)>;

    Decoder();
    ~Decoder();

    bool init();
    void destroy();
    
    // Decodes raw HEVC/H.265 data, calls callback for each fully decoded frame
    void decode(const uint8_t* data, int size, FrameCallback callback);

private:
    const AVCodec* codec_;
    AVCodecContext* codec_ctx_;
    AVCodecParserContext* parser_;
    AVPacket* pkt_;
    AVFrame* frame_;
    AVFrame* sw_frame_; // For hardware-to-software transfer (legacy single frame)
    AVBufferRef* hw_device_ctx_;
    AVBufferRef* hw_frames_ctx_; // Hardware frame pool for zero-copy decoding
    
    // Frame pool for recycled software frames (avoids per-frame allocation)
    std::queue<AVFrame*> free_sw_frames_;
    std::mutex pool_mutex_;
    
    std::mutex decode_mutex_;
    
    bool init_hw_frame_pool(int width, int height);
    AVFrame* acquire_sw_frame(int width, int height);
    void release_sw_frame(AVFrame* frame);
};
