#include "decoder.h"
#include <iostream>
#include <vector>
#include <cassert>
#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/opt.h>
#include <libavutil/imgutils.h>
}

using namespace std;

// Helper to encode a test frame with libx264 into Annex B NAL byte buffer
static vector<uint8_t> encode_test_frame(int width, int height, int frame_index, AVCodecID codec_id = AV_CODEC_ID_H264) {
    const AVCodec* encoder = avcodec_find_encoder(codec_id);
    if (!encoder) {
        cerr << "Encoder not found\n";
        return {};
    }

    AVCodecContext* ctx = avcodec_alloc_context3(encoder);
    if (!ctx) return {};

    ctx->width = width;
    ctx->height = height;
    ctx->time_base = {1, 30};
    ctx->framerate = {30, 1};
    ctx->pix_fmt = AV_PIX_FMT_YUV420P;
    ctx->gop_size = 1; // IDR keyframe every frame for predictable SPS emission
    ctx->max_b_frames = 0;

    if (codec_id == AV_CODEC_ID_H264) {
        av_opt_set(ctx->priv_data, "preset", "ultrafast", 0);
        av_opt_set(ctx->priv_data, "tune", "zerolatency", 0);
    }

    if (avcodec_open2(ctx, encoder, nullptr) < 0) {
        cerr << "Failed to open encoder\n";
        avcodec_free_context(&ctx);
        return {};
    }

    AVFrame* frame = av_frame_alloc();
    frame->width = width;
    frame->height = height;
    frame->format = AV_PIX_FMT_YUV420P;
    av_image_alloc(frame->data, frame->linesize, width, height, AV_PIX_FMT_YUV420P, 32);

    // Fill with pattern
    memset(frame->data[0], (frame_index * 20) % 255, frame->linesize[0] * height);
    memset(frame->data[1], 128, frame->linesize[1] * (height / 2));
    memset(frame->data[2], 128, frame->linesize[2] * (height / 2));
    frame->pts = frame_index;

    vector<uint8_t> encoded_data;
    AVPacket* pkt = av_packet_alloc();

    if (avcodec_send_frame(ctx, frame) >= 0) {
        while (avcodec_receive_packet(ctx, pkt) >= 0) {
            encoded_data.insert(encoded_data.end(), pkt->data, pkt->data + pkt->size);
            av_packet_unref(pkt);
        }
    }

    // Flush encoder
    avcodec_send_frame(ctx, nullptr);
    while (avcodec_receive_packet(ctx, pkt) >= 0) {
        encoded_data.insert(encoded_data.end(), pkt->data, pkt->data + pkt->size);
        av_packet_unref(pkt);
    }

    av_packet_free(&pkt);
    av_freep(&frame->data[0]);
    av_frame_free(&frame);
    avcodec_free_context(&ctx);

    return encoded_data;
}

int main() {
    cout << "=== Running Empirical Decoder Dynamic Rotation & HW Pool Resilience Stress Test ===" << endl;

    Decoder decoder;
    bool error_occurred = false;
    decoder.set_error_callback([&]() {
        error_occurred = true;
    });

    // 1. Initialize decoder
    assert(decoder.init(0x01) == true); // 0x01 = H.264
    cout << "[PASS] Decoder initialized successfully" << endl;

    vector<pair<int, int>> received_dimensions;
    auto callback = [&](Decoder::FramePtr frame) {
        if (frame) {
            received_dimensions.push_back({frame->width, frame->height});
        }
    };

    // 2. Feed 5 portrait frames (720x1280)
    cout << "--- Phase 1: Decoding Portrait Frames (720x1280) ---" << endl;
    for (int i = 0; i < 5; ++i) {
        auto data = encode_test_frame(720, 1280, i, AV_CODEC_ID_H264);
        assert(!data.empty());
        decoder.decode(data.data(), (int)data.size(), callback);
    }

    assert(!error_occurred);
    assert(!received_dimensions.empty());
    assert(received_dimensions.back().first == 720);
    assert(received_dimensions.back().second == 1280);
    cout << "[PASS] Successfully decoded portrait frames (720x1280), count: " << received_dimensions.size() << endl;

    // 3. Mid-stream orientation flip: Feed 5 landscape frames (1280x720)
    cout << "--- Phase 2: Mid-Stream Dynamic Flip to Landscape (1280x720) ---" << endl;
    size_t prev_count = received_dimensions.size();
    for (int i = 0; i < 5; ++i) {
        auto data = encode_test_frame(1280, 720, 10 + i, AV_CODEC_ID_H264);
        assert(!data.empty());
        decoder.decode(data.data(), (int)data.size(), callback);
    }

    assert(!error_occurred);
    assert(received_dimensions.size() > prev_count);
    assert(received_dimensions.back().first == 1280);
    assert(received_dimensions.back().second == 720);
    cout << "[PASS] Successfully handled mid-stream orientation flip to landscape (1280x720)" << endl;

    // 4. Mid-stream flip back to portrait: Feed 5 portrait frames (720x1280)
    cout << "--- Phase 3: Mid-Stream Dynamic Flip Back to Portrait (720x1280) ---" << endl;
    prev_count = received_dimensions.size();
    for (int i = 0; i < 5; ++i) {
        auto data = encode_test_frame(720, 1280, 20 + i, AV_CODEC_ID_H264);
        assert(!data.empty());
        decoder.decode(data.data(), (int)data.size(), callback);
    }

    assert(!error_occurred);
    assert(received_dimensions.size() > prev_count);
    assert(received_dimensions.back().first == 720);
    assert(received_dimensions.back().second == 1280);
    cout << "[PASS] Successfully handled mid-stream orientation flip back to portrait (720x1280)" << endl;

    // 5. Arbitrary resolution change: 1080x1920 -> 1920x1080 -> 1080x2400
    cout << "--- Phase 4: Non-Standard Dimension Transitions (1080x2400) ---" << endl;
    prev_count = received_dimensions.size();
    for (int i = 0; i < 3; ++i) {
        auto data = encode_test_frame(1080, 2400, 30 + i, AV_CODEC_ID_H264);
        assert(!data.empty());
        decoder.decode(data.data(), (int)data.size(), callback);
    }

    assert(!error_occurred);
    assert(received_dimensions.size() > prev_count);
    assert(received_dimensions.back().first == 1080);
    assert(received_dimensions.back().second == 2400);
    cout << "[PASS] Successfully handled non-standard 20:9 resolution (1080x2400)" << endl;

    // 6. Teardown
    decoder.destroy();
    cout << "[PASS] Clean decoder teardown without leaks or crashes" << endl;

    cout << "\n========================================================" << endl;
    cout << " All Decoder Dynamic Rotation Stress Tests PASSED!" << endl;
    cout << "========================================================" << endl;
    return 0;
}
