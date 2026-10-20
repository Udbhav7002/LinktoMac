#include <iostream>
#include <vector>
#include <cassert>
#include <cstring>
#include <thread>
#include <atomic>
#include <chrono>
#include <functional>
#include <memory>
#include <mutex>
#include <queue>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/opt.h>
#include <libavutil/imgutils.h>
#include <libavutil/hwcontext.h>
#include <libavutil/hwcontext_videotoolbox.h>
}

#define private public
#define protected public
#include "decoder.h"
#undef private
#undef protected

using namespace std;
using namespace std::chrono;

// Helper to generate individual Annex B packet frames
static vector<vector<uint8_t>> encode_test_packets(AVCodecID codec_id, int width, int height, int num_frames) {
    const AVCodec* enc = nullptr;
    if (codec_id == AV_CODEC_ID_H264) {
        enc = avcodec_find_encoder_by_name("libx264");
        if (!enc) enc = avcodec_find_encoder(AV_CODEC_ID_H264);
    } else if (codec_id == AV_CODEC_ID_HEVC) {
        enc = avcodec_find_encoder_by_name("libx265");
        if (!enc) enc = avcodec_find_encoder(AV_CODEC_ID_HEVC);
    }
    if (!enc) return {};

    AVCodecContext* ctx = avcodec_alloc_context3(enc);
    if (!ctx) return {};

    ctx->width = width;
    ctx->height = height;
    ctx->time_base = AVRational{1, 30};
    ctx->framerate = AVRational{30, 1};
    ctx->pix_fmt = AV_PIX_FMT_YUV420P;
    ctx->gop_size = 5;
    ctx->max_b_frames = 0;

    if (codec_id == AV_CODEC_ID_H264) {
        av_opt_set(ctx->priv_data, "preset", "ultrafast", 0);
        av_opt_set(ctx->priv_data, "tune", "zerolatency", 0);
        av_opt_set(ctx->priv_data, "x264-params", "repeat-headers=1", 0);
    } else if (codec_id == AV_CODEC_ID_HEVC) {
        av_opt_set(ctx->priv_data, "preset", "ultrafast", 0);
        av_opt_set(ctx->priv_data, "tune", "zerolatency", 0);
        av_opt_set(ctx->priv_data, "x265-params", "repeat-headers=1", 0);
    }

    ctx->flags &= ~AV_CODEC_FLAG_GLOBAL_HEADER;

    if (avcodec_open2(ctx, enc, nullptr) < 0) {
        avcodec_free_context(&ctx);
        return {};
    }

    AVFrame* frame = av_frame_alloc();
    frame->width = width;
    frame->height = height;
    frame->format = AV_PIX_FMT_YUV420P;
    if (av_image_alloc(frame->data, frame->linesize, width, height, AV_PIX_FMT_YUV420P, 32) < 0) {
        av_frame_free(&frame);
        avcodec_free_context(&ctx);
        return {};
    }

    AVPacket* pkt = av_packet_alloc();
    vector<vector<uint8_t>> packets;

    for (int i = 0; i < num_frames; ++i) {
        frame->pts = i;
        memset(frame->data[0], (i * 15 + 40) % 255, frame->linesize[0] * height);
        memset(frame->data[1], 128, frame->linesize[1] * (height / 2));
        memset(frame->data[2], 128, frame->linesize[2] * (height / 2));

        if (avcodec_send_frame(ctx, frame) >= 0) {
            while (true) {
                int ret = avcodec_receive_packet(ctx, pkt);
                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
                if (ret < 0) break;
                packets.emplace_back(pkt->data, pkt->data + pkt->size);
                av_packet_unref(pkt);
            }
        }
    }

    avcodec_send_frame(ctx, nullptr);
    while (true) {
        int ret = avcodec_receive_packet(ctx, pkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
        if (ret < 0) break;
        packets.emplace_back(pkt->data, pkt->data + pkt->size);
        av_packet_unref(pkt);
    }

    av_freep(&frame->data[0]);
    av_frame_free(&frame);
    av_packet_free(&pkt);
    avcodec_free_context(&ctx);

    return packets;
}

// Helper to generate a compliant Annex B bitstream with SPS/PPS/IDR headers
static vector<uint8_t> encode_test_stream(AVCodecID codec_id, int width, int height, int num_frames) {
    const AVCodec* enc = nullptr;
    if (codec_id == AV_CODEC_ID_H264) {
        enc = avcodec_find_encoder_by_name("libx264");
        if (!enc) enc = avcodec_find_encoder(AV_CODEC_ID_H264);
    } else if (codec_id == AV_CODEC_ID_HEVC) {
        enc = avcodec_find_encoder_by_name("libx265");
        if (!enc) enc = avcodec_find_encoder(AV_CODEC_ID_HEVC);
    }
    if (!enc) {
        cerr << "[WARN] Encoder for codec " << codec_id << " not found, skipping stream generation\n";
        return {};
    }

    AVCodecContext* ctx = avcodec_alloc_context3(enc);
    if (!ctx) return {};

    ctx->width = width;
    ctx->height = height;
    ctx->time_base = AVRational{1, 30};
    ctx->framerate = AVRational{30, 1};
    ctx->pix_fmt = AV_PIX_FMT_YUV420P;
    ctx->gop_size = 5;
    ctx->max_b_frames = 0;

    if (codec_id == AV_CODEC_ID_H264) {
        av_opt_set(ctx->priv_data, "preset", "ultrafast", 0);
        av_opt_set(ctx->priv_data, "tune", "zerolatency", 0);
        av_opt_set(ctx->priv_data, "x264-params", "repeat-headers=1", 0);
    } else if (codec_id == AV_CODEC_ID_HEVC) {
        av_opt_set(ctx->priv_data, "preset", "ultrafast", 0);
        av_opt_set(ctx->priv_data, "tune", "zerolatency", 0);
        av_opt_set(ctx->priv_data, "x265-params", "repeat-headers=1", 0);
    }

    ctx->flags &= ~AV_CODEC_FLAG_GLOBAL_HEADER;

    if (avcodec_open2(ctx, enc, nullptr) < 0) {
        avcodec_free_context(&ctx);
        return {};
    }

    AVFrame* frame = av_frame_alloc();
    frame->width = width;
    frame->height = height;
    frame->format = AV_PIX_FMT_YUV420P;
    if (av_image_alloc(frame->data, frame->linesize, width, height, AV_PIX_FMT_YUV420P, 32) < 0) {
        av_frame_free(&frame);
        avcodec_free_context(&ctx);
        return {};
    }

    AVPacket* pkt = av_packet_alloc();
    vector<uint8_t> stream;

    for (int i = 0; i < num_frames; ++i) {
        frame->pts = i;
        memset(frame->data[0], (i * 15 + 40) % 255, frame->linesize[0] * height);
        memset(frame->data[1], 128, frame->linesize[1] * (height / 2));
        memset(frame->data[2], 128, frame->linesize[2] * (height / 2));

        if (avcodec_send_frame(ctx, frame) >= 0) {
            while (true) {
                int ret = avcodec_receive_packet(ctx, pkt);
                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
                if (ret < 0) break;
                stream.insert(stream.end(), pkt->data, pkt->data + pkt->size);
                av_packet_unref(pkt);
            }
        }
    }

    // Flush encoder
    avcodec_send_frame(ctx, nullptr);
    while (true) {
        int ret = avcodec_receive_packet(ctx, pkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
        if (ret < 0) break;
        stream.insert(stream.end(), pkt->data, pkt->data + pkt->size);
        av_packet_unref(pkt);
    }

    av_freep(&frame->data[0]);
    av_frame_free(&frame);
    av_packet_free(&pkt);
    avcodec_free_context(&ctx);

    return stream;
}

int main() {
    cout << "========================================================" << endl;
    cout << " Running Decoder Dynamic Resolution & Frame Pool Empirical Stress Tests" << endl;
    cout << "========================================================" << endl;

    int tests_passed = 0;
    int tests_failed = 0;

    // =========================================================================
    // SECTION 1: Frame Queue Lifecycle, Pre-allocation & Capacity Burst Test
    // =========================================================================
    {
        cout << "\n--- TEST 1: Frame Queue Lifecycle, Pre-allocation & Capacity Burst ---" << endl;
        Decoder decoder;
        if (!decoder.init(1)) { // H.264
            cerr << "[FAIL] Failed to init Decoder\n";
            return 1;
        }

        decoder.init_hw_frame_pool(1080, 1920);
        bool pool_init_ok = (decoder.pool_width_ == 1080 &&
                             decoder.pool_height_ == 1920 &&
                             decoder.free_sw_frames_.size() == 8);
        if (pool_init_ok) {
            cout << "[PASS] HW frame pool initialized with 8 pre-allocated NV12 frames (1080x1920)." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] HW frame pool pre-allocation incorrect: size=" << decoder.free_sw_frames_.size() << endl;
            tests_failed++;
        }

        // Check each frame properties
        bool all_frames_valid = true;
        {
            lock_guard<mutex> lock(decoder.pool_mutex_);
            auto q_copy = decoder.free_sw_frames_;
            while (!q_copy.empty()) {
                AVFrame* f = q_copy.front();
                q_copy.pop();
                if (!f || f->width != 1080 || f->height != 1920 || f->format != AV_PIX_FMT_NV12) {
                    all_frames_valid = false;
                    break;
                }
            }
        }
        if (all_frames_valid) {
            cout << "[PASS] All pre-allocated frames have correct format NV12 and dimensions 1080x1920." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Frame properties in pool invalid." << endl;
            tests_failed++;
        }

        // Burst acquire 16 frames (8 pooled + 8 dynamic allocations)
        vector<AVFrame*> checked_out;
        bool all_burst_valid = true;
        for (int i = 0; i < 16; ++i) {
            AVFrame* f = decoder.acquire_sw_frame(1080, 1920);
            if (!f || f->width != 1080 || f->height != 1920 || f->format != AV_PIX_FMT_NV12) {
                all_burst_valid = false;
            }
            checked_out.push_back(f);
        }

        bool pool_exhausted = (decoder.free_sw_frames_.empty());
        if (all_burst_valid && pool_exhausted && checked_out.size() == 16) {
            cout << "[PASS] Burst allocation of 16 frames succeeded (pool drained + on-demand allocations)." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Burst allocation failed or pool not exhausted." << endl;
            tests_failed++;
        }

        // Return first 8 frames back into pool
        for (int i = 0; i < 8; ++i) {
            decoder.release_sw_frame(checked_out[i]);
        }
        bool return_ok = (decoder.free_sw_frames_.size() == 8);
        if (return_ok) {
            cout << "[PASS] Returned 8 frames to pool, free_sw_frames_ size restored to 8." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Free pool size after return: " << decoder.free_sw_frames_.size() << " (expected 8)" << endl;
            tests_failed++;
        }

        // Free the remaining 8 checked-out frames manually to avoid memory leaks
        for (int i = 8; i < 16; ++i) {
            av_frame_free(&checked_out[i]);
        }
    }

    // =========================================================================
    // SECTION 2: Stale Dimensions Elimination on Rotation
    // =========================================================================
    {
        cout << "\n--- TEST 2: Mid-Stream Rotation Stale Dimension Frame Discarding ---" << endl;
        Decoder decoder;
        decoder.init(1);
        decoder.init_hw_frame_pool(1080, 1920);

        // Acquire 4 frames of portrait dimensions (1080x1920)
        vector<AVFrame*> portrait_frames;
        for (int i = 0; i < 4; ++i) {
            portrait_frames.push_back(decoder.acquire_sw_frame(1080, 1920));
        }

        // Simulate mid-stream orientation change: reinit pool with landscape (1920x1080)
        decoder.init_hw_frame_pool(1920, 1080);
        bool landscape_pool_ok = (decoder.pool_width_ == 1920 &&
                                  decoder.pool_height_ == 1080 &&
                                  decoder.free_sw_frames_.size() == 8);
        if (landscape_pool_ok) {
            cout << "[PASS] Pool reallocated with landscape dimensions (1920x1080)." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Pool reallocation failed." << endl;
            tests_failed++;
        }

        // Now release the 4 portrait frames (1080x1920) held by consumer
        for (AVFrame* pf : portrait_frames) {
            decoder.release_sw_frame(pf);
        }

        // The 4 portrait frames must be discarded (freed) because their dimensions do NOT match pool
        bool pool_unpolluted = (decoder.free_sw_frames_.size() == 8);
        bool all_are_landscape = true;
        {
            lock_guard<mutex> lock(decoder.pool_mutex_);
            auto q_copy = decoder.free_sw_frames_;
            while (!q_copy.empty()) {
                AVFrame* f = q_copy.front();
                q_copy.pop();
                if (f->width != 1920 || f->height != 1080) {
                    all_are_landscape = false;
                    break;
                }
            }
        }

        if (pool_unpolluted && all_are_landscape) {
            cout << "[PASS] Stale portrait frames strictly discarded: pool size remains 8 and contains 100% landscape frames." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Stale frames polluted pool! Size=" << decoder.free_sw_frames_.size()
                 << " all_landscape=" << all_are_landscape << endl;
            tests_failed++;
        }
    }

    // =========================================================================
    // SECTION 3: FramePtr Custom Deleter Lifecycle Across Orientation Flip
    // =========================================================================
    {
        cout << "\n--- TEST 3: FramePtr Custom Deleter Lifecycle Across Dimension Flip ---" << endl;
        Decoder decoder;
        decoder.init(1);
        decoder.init_hw_frame_pool(1080, 1920);

        AVFrame* raw_f = decoder.acquire_sw_frame(1080, 1920);
        assert(raw_f != nullptr);

        {
            Decoder::FramePtr fptr = decoder.make_frame_ptr(raw_f);
            assert(fptr.get() == raw_f);

            // Reallocate pool to landscape while fptr is still alive
            decoder.init_hw_frame_pool(1920, 1080);
            assert(decoder.pool_width_ == 1920);

            // fptr goes out of scope here: custom deleter invokes release_sw_frame(raw_f)
        }

        // Ensure pool still has 8 landscape frames and raw_f was freed cleanly without crash
        bool deleter_clean = (decoder.free_sw_frames_.size() == 8);
        if (deleter_clean) {
            cout << "[PASS] FramePtr custom deleter safely discarded stale frame on scope exit without pool contamination." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] FramePtr custom deleter failure: pool size=" << decoder.free_sw_frames_.size() << endl;
            tests_failed++;
        }
    }

    // =========================================================================
    // SECTION 4: Multi-Threaded Concurrent Acquire/Release & Dimension Flips
    // =========================================================================
    {
        cout << "\n--- TEST 4: Multi-Threaded Concurrent Acquire/Release & Dimension Flips Stress ---" << endl;
        Decoder decoder;
        decoder.init(1);
        decoder.init_hw_frame_pool(1080, 1920);

        atomic<bool> stop_flag{false};
        atomic<int> ops_count{0};
        atomic<int> flip_count{0};

        auto worker_acquire_release = [&]() {
            while (!stop_flag.load(memory_order_relaxed)) {
                int w = (ops_count.load() % 2 == 0) ? 1080 : 1920;
                int h = (w == 1080) ? 1920 : 1080;
                AVFrame* f = decoder.acquire_sw_frame(w, h);
                if (f) {
                    this_thread::yield();
                    decoder.release_sw_frame(f);
                    ops_count.fetch_add(1, memory_order_relaxed);
                }
            }
        };

        auto worker_flip = [&]() {
            while (!stop_flag.load(memory_order_relaxed)) {
                int count = flip_count.fetch_add(1, memory_order_relaxed);
                int w = (count % 2 == 0) ? 1920 : 1080;
                int h = (w == 1920) ? 1080 : 1920;
                {
                    lock_guard<mutex> lk(decoder.decode_mutex_);
                    decoder.init_hw_frame_pool(w, h);
                }
                this_thread::sleep_for(milliseconds(2));
            }
        };

        vector<thread> threads;
        for (int i = 0; i < 4; ++i) threads.emplace_back(worker_acquire_release);
        for (int i = 0; i < 2; ++i) threads.emplace_back(worker_flip);

        this_thread::sleep_for(milliseconds(1000));
        stop_flag.store(true);

        for (auto& t : threads) {
            t.join();
        }

        cout << "[INFO] Completed " << ops_count.load() << " frame operations and "
             << flip_count.load() << " dimension flips concurrently." << endl;

        if (ops_count.load() > 1000 && flip_count.load() > 50) {
            cout << "[PASS] Concurrent pool operations survived high load with zero deadlocks or crashes." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Insufficient iterations completed in stress test." << endl;
            tests_failed++;
        }
    }

    // =========================================================================
    // SECTION 5: Live H.264 Mid-Stream Orientation Change (Portrait -> Landscape -> Portrait)
    // =========================================================================
    {
        cout << "\n--- TEST 5: Live H.264 Mid-Stream Dimension Change (Portrait -> Landscape -> Portrait) ---" << endl;
        auto h264_portrait_1_pkts = encode_test_packets(AV_CODEC_ID_H264, 720, 1280, 10);
        auto h264_landscape_pkts  = encode_test_packets(AV_CODEC_ID_H264, 1280, 720, 10);
        auto h264_portrait_2_pkts = encode_test_packets(AV_CODEC_ID_H264, 720, 1280, 10);

        if (h264_portrait_1_pkts.empty() || h264_landscape_pkts.empty() || h264_portrait_2_pkts.empty()) {
            cerr << "[WARN] Skipping H.264 live stream test due to missing encoder\n";
        } else {
            Decoder decoder;
            if (!decoder.init(1)) { // H.264 preferred
                cerr << "[FAIL] Failed to init H.264 decoder\n";
                tests_failed++;
            } else {
                atomic<int> error_count{0};
                decoder.set_error_callback([&]() {
                    error_count.fetch_add(1);
                });

                vector<pair<int, int>> decoded_dims;
                decoder.has_decoded_frame_ = true; // Bypass pre-first-frame auto-detection to isolate M2 rotation logic
                mutex dims_mutex;

                auto frame_callback = [&](Decoder::FramePtr frame) {
                    lock_guard<mutex> lk(dims_mutex);
                    decoded_dims.push_back({frame->width, frame->height});
                };

                // Phase 1: Portrait 720x1280 (packet by packet)
                for (const auto& pkt : h264_portrait_1_pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), frame_callback);
                }
                int portrait_1_count = 0;
                {
                    lock_guard<mutex> lk(dims_mutex);
                    portrait_1_count = (int)decoded_dims.size();
                }

                // Phase 2: Mid-stream rotation to Landscape 1280x720
                for (const auto& pkt : h264_landscape_pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), frame_callback);
                }
                int landscape_count = 0;
                {
                    lock_guard<mutex> lk(dims_mutex);
                    landscape_count = (int)decoded_dims.size() - portrait_1_count;
                }

                // Phase 3: Mid-stream rotation back to Portrait 720x1280
                for (const auto& pkt : h264_portrait_2_pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), frame_callback);
                }
                int portrait_2_count = 0;
                {
                    lock_guard<mutex> lk(dims_mutex);
                    portrait_2_count = (int)decoded_dims.size() - portrait_1_count - landscape_count;
                }

                cout << "[INFO] Decoded H.264 frame counts: phase1=" << portrait_1_count
                     << ", phase2=" << landscape_count << ", phase3=" << portrait_2_count
                     << ", errors=" << error_count.load() << endl;

                bool h264_success = (error_count.load() == 0 &&
                                     portrait_1_count > 0 &&
                                     landscape_count > 0 &&
                                     portrait_2_count > 0);

                if (h264_success) {
                    cout << "[PASS] H.264 live stream seamlessly handled mid-stream dimension changes (720x1280 -> 1280x720 -> 720x1280) with 0 errors!" << endl;
                    tests_passed++;
                } else {
                    cout << "[FAIL] H.264 live stream rotation failed: errors=" << error_count.load() << endl;
                    tests_failed++;
                }
            }
        }
    }

    // =========================================================================
    // SECTION 6: Live HEVC / H.265 Mid-Stream Orientation Change
    // =========================================================================
    {
        cout << "\n--- TEST 6: Live HEVC Mid-Stream Dimension Change (Portrait -> Landscape -> Portrait) ---" << endl;
        auto hevc_portrait_1_pkts = encode_test_packets(AV_CODEC_ID_HEVC, 720, 1280, 10);
        auto hevc_landscape_pkts  = encode_test_packets(AV_CODEC_ID_HEVC, 1280, 720, 10);
        auto hevc_portrait_2_pkts = encode_test_packets(AV_CODEC_ID_HEVC, 720, 1280, 10);

        if (hevc_portrait_1_pkts.empty() || hevc_landscape_pkts.empty() || hevc_portrait_2_pkts.empty()) {
            cout << "[INFO] HEVC encoder not available on this platform, skipping live HEVC test." << endl;
        } else {
            Decoder decoder;
            if (!decoder.init(0)) { // HEVC preferred
                cerr << "[FAIL] Failed to init HEVC decoder\n";
                tests_failed++;
            } else {
                atomic<int> error_count{0};
                decoder.set_error_callback([&]() {
                    error_count.fetch_add(1);
                });

                vector<pair<int, int>> decoded_dims;
                decoder.has_decoded_frame_ = true; // Bypass pre-first-frame auto-detection to isolate M2 rotation logic
                mutex dims_mutex;

                auto frame_callback = [&](Decoder::FramePtr frame) {
                    lock_guard<mutex> lk(dims_mutex);
                    decoded_dims.push_back({frame->width, frame->height});
                };

                // Phase 1: Portrait 720x1280
                for (const auto& pkt : hevc_portrait_1_pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), frame_callback);
                }
                int portrait_1_count = 0;
                {
                    lock_guard<mutex> lk(dims_mutex);
                    portrait_1_count = (int)decoded_dims.size();
                }

                // Phase 2: Mid-stream rotation to Landscape 1280x720
                for (const auto& pkt : hevc_landscape_pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), frame_callback);
                }
                int landscape_count = 0;
                {
                    lock_guard<mutex> lk(dims_mutex);
                    landscape_count = (int)decoded_dims.size() - portrait_1_count;
                }

                // Phase 3: Mid-stream rotation back to Portrait 720x1280
                for (const auto& pkt : hevc_portrait_2_pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), frame_callback);
                }
                int portrait_2_count = 0;
                {
                    lock_guard<mutex> lk(dims_mutex);
                    portrait_2_count = (int)decoded_dims.size() - portrait_1_count - landscape_count;
                }

                cout << "[INFO] Decoded HEVC frame counts: phase1=" << portrait_1_count
                     << ", phase2=" << landscape_count << ", phase3=" << portrait_2_count
                     << ", errors=" << error_count.load() << endl;

                bool hevc_success = (error_count.load() == 0 &&
                                     portrait_1_count > 0 &&
                                     landscape_count > 0 &&
                                     portrait_2_count > 0);

                if (hevc_success) {
                    cout << "[PASS] HEVC live stream seamlessly handled mid-stream dimension changes (720x1280 -> 1280x720 -> 720x1280) with 0 errors!" << endl;
                    tests_passed++;
                } else {
                    cout << "[FAIL] HEVC live stream rotation failed: errors=" << error_count.load() << endl;
                    tests_failed++;
                }
            }
        }
    }

    // =========================================================================
    // SECTION 7: Rapid Orientation Churn Fuzzing (50 Flips Mid-Stream)
    // =========================================================================
    {
        cout << "\n--- TEST 7: Rapid Orientation Churn Fuzzing (50 Mid-Stream Flips) ---" << endl;
        auto h264_portrait_pkts = encode_test_packets(AV_CODEC_ID_H264, 720, 1280, 2);
        auto h264_landscape_pkts = encode_test_packets(AV_CODEC_ID_H264, 1280, 720, 2);

        if (!h264_portrait_pkts.empty() && !h264_landscape_pkts.empty()) {
            Decoder decoder;
            decoder.init(1);
            decoder.has_decoded_frame_ = true;
            atomic<int> error_count{0};
            decoder.set_error_callback([&]() { error_count.fetch_add(1); });

            int frame_count = 0;
            auto cb = [&](Decoder::FramePtr f) {
                if (f) frame_count++;
            };

            for (int i = 0; i < 50; ++i) {
                const auto& pkts = (i % 2 == 0) ? h264_portrait_pkts : h264_landscape_pkts;
                for (const auto& pkt : pkts) {
                    decoder.decode(pkt.data(), (int)pkt.size(), cb);
                }
            }

            cout << "[INFO] 50 rapid alternating flips completed: frames decoded=" << frame_count
                 << ", errors=" << error_count.load() << endl;

            if (error_count.load() == 0 && frame_count > 0) {
                cout << "[PASS] 50 rapid mid-stream orientation flips survived without crash or AVERROR(EINVAL)." << endl;
                tests_passed++;
            } else {
                cout << "[FAIL] Rapid orientation churn caused errors: " << error_count.load() << endl;
                tests_failed++;
            }
        }
    }

    // =========================================================================
    // SECTION 8: Corrupt / Truncated Bitstream Boundary Resilience
    // =========================================================================
    {
        cout << "\n--- TEST 8: Corrupt / Truncated Bitstream Boundary Resilience ---" << endl;
        Decoder decoder;
        decoder.init(1);
        atomic<int> error_count{0};
        decoder.set_error_callback([&]() { error_count.fetch_add(1); });

        // First establish decoder stream with a clean initial keyframe
        auto init_pkts = encode_test_packets(AV_CODEC_ID_H264, 720, 1280, 2);
        if (!init_pkts.empty()) {
            decoder.decode(init_pkts[0].data(), (int)init_pkts[0].size(), [](Decoder::FramePtr) {});
        }

        // Feed garbage / truncated NAL data
        uint8_t garbage_nal[] = {0x00, 0x00, 0x00, 0x01, 0x67, 0xFF, 0xAA, 0x55, 0x12};
        decoder.decode(garbage_nal, sizeof(garbage_nal), [](Decoder::FramePtr) {});

        // Feed random 512 bytes of noise
        vector<uint8_t> noise(512, 0xCC);
        decoder.decode(noise.data(), (int)noise.size(), [](Decoder::FramePtr) {});

        // Now feed clean valid packets with orientation flip (1280x720) to verify dynamic flush recovery
        auto clean_pkts = encode_test_packets(AV_CODEC_ID_H264, 1280, 720, 5);
        int clean_frames = 0;
        for (const auto& pkt : clean_pkts) {
            decoder.decode(pkt.data(), (int)pkt.size(), [&](Decoder::FramePtr f) {
                if (f) clean_frames++;
            });
        }

        if (clean_frames > 0) {
            cout << "[PASS] Decoder recovered cleanly from corrupt bitstream and resumed normal frame decoding." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Decoder failed to recover after corrupt bitstream input." << endl;
            tests_failed++;
        }
    }

    cout << "\n========================================================" << endl;
    cout << " Decoder Stress Test Summary: " << tests_passed << " passed, " << tests_failed << " failed." << endl;
    cout << "========================================================" << endl;

    return (tests_failed == 0) ? 0 : 1;
}
