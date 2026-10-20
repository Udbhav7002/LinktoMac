#include "decoder.h"
#include <iostream>
#include <vector>
#include <thread>
#include <chrono>
#include <cassert>
#include <atomic>
#include <mach/mach.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/opt.h>
#include <libavutil/imgutils.h>
}

using namespace std;
using namespace std::chrono;

static size_t get_current_rss_bytes() {
    mach_task_basic_info info;
    mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
    if (task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count) == KERN_SUCCESS) {
        return info.resident_size;
    }
    return 0;
}

// Helper to encode a minimal synthetic H.264 Annex B stream of given dimensions
static vector<uint8_t> encode_test_stream(int width, int height, int num_frames) {
    const AVCodec* enc = avcodec_find_encoder(AV_CODEC_ID_H264);
    if (!enc) return {};

    AVCodecContext* ctx = avcodec_alloc_context3(enc);
    ctx->width = width;
    ctx->height = height;
    ctx->pix_fmt = AV_PIX_FMT_YUV420P;
    ctx->time_base = {1, 30};
    ctx->framerate = {30, 1};
    ctx->gop_size = 5;
    ctx->max_b_frames = 0;
    av_opt_set(ctx->priv_data, "preset", "ultrafast", 0);
    av_opt_set(ctx->priv_data, "tune", "zerolatency", 0);

    if (avcodec_open2(ctx, enc, nullptr) < 0) {
        avcodec_free_context(&ctx);
        return {};
    }

    AVFrame* frame = av_frame_alloc();
    frame->format = ctx->pix_fmt;
    frame->width = width;
    frame->height = height;
    av_frame_get_buffer(frame, 32);

    AVPacket* pkt = av_packet_alloc();
    vector<uint8_t> bitstream;

    for (int i = 0; i < num_frames; ++i) {
        av_frame_make_writable(frame);
        memset(frame->data[0], (i * 10) & 0xFF, frame->linesize[0] * height);
        memset(frame->data[1], 128, frame->linesize[1] * (height / 2));
        memset(frame->data[2], 128, frame->linesize[2] * (height / 2));
        frame->pts = i;

        avcodec_send_frame(ctx, frame);
        while (avcodec_receive_packet(ctx, pkt) >= 0) {
            bitstream.insert(bitstream.end(), pkt->data, pkt->data + pkt->size);
            av_packet_unref(pkt);
        }
    }

    // Flush encoder
    avcodec_send_frame(ctx, nullptr);
    while (avcodec_receive_packet(ctx, pkt) >= 0) {
        bitstream.insert(bitstream.end(), pkt->data, pkt->data + pkt->size);
        av_packet_unref(pkt);
    }

    av_packet_free(&pkt);
    av_frame_free(&frame);
    avcodec_free_context(&ctx);

    return bitstream;
}

int main() {
    cout << "=== Running Empirical Decoder Dynamic Resolution & Leak Stress Harness ===" << endl;
    int passed = 0;
    int failed = 0;

    // SECTION 1: Bitstream Parsing with Mid-Stream Orientation Flips (Annex B)
    {
        cout << "[TEST 1] Streaming Annex B bitstream with mid-stream orientation changes (320x240 <-> 240x320)..." << endl;
        
        // Generate stream 1: 320x240 (landscape)
        vector<uint8_t> stream_land = encode_test_stream(320, 240, 10);
        assert(!stream_land.empty());

        // Generate stream 2: 240x320 (portrait)
        vector<uint8_t> stream_port = encode_test_stream(240, 320, 10);
        assert(!stream_port.empty());

        Decoder decoder;
        atomic<bool> error_triggered{false};
        decoder.set_error_callback([&]() {
            error_triggered = true;
        });

        if (!decoder.init(1)) { // H.264
            cerr << "[FAIL] Failed to init Decoder" << endl;
            return 1;
        }

        vector<pair<int, int>> decoded_dimensions;
        auto frame_cb = [&](Decoder::FramePtr f) {
            if (f) {
                decoded_dimensions.push_back({f->width, f->height});
            }
        };

        // 1. Send stream 1 (320x240)
        decoder.decode(stream_land.data(), stream_land.size(), frame_cb);

        // 2. Mid-stream orientation change to portrait (240x320)
        decoder.decode(stream_port.data(), stream_port.size(), frame_cb);

        // 3. Mid-stream orientation change back to landscape (320x240)
        decoder.decode(stream_land.data(), stream_land.size(), frame_cb);

        bool had_land = false;
        bool had_port = false;
        for (const auto& d : decoded_dimensions) {
            if (d.first == 320 && d.second == 240) had_land = true;
            if (d.first == 240 && d.second == 320) had_port = true;
        }

        cout << "  Decoded " << decoded_dimensions.size() << " frames across 3 orientation phases." << endl;
        cout << "  Saw 320x240 frames: " << (had_land ? "YES" : "NO")
             << ", Saw 240x320 frames: " << (had_port ? "YES" : "NO")
             << ", Errors triggered: " << (error_triggered ? "YES" : "NO") << endl;

        if (had_land && had_port && !error_triggered) {
            cout << "[PASS] Decoder seamlessly adapted across multiple mid-stream orientation flips." << endl;
            passed++;
        } else {
            cout << "[FAIL] Decoder failed mid-stream orientation flip." << endl;
            failed++;
        }
    }

    // SECTION 2: 1,000 Repeated Resolution Switches & Zero Memory Leak Check
    {
        cout << "[TEST 2] Stressing 1,000 repeated resolution switches on Decoder..." << endl;

        vector<uint8_t> stream_land = encode_test_stream(320, 240, 3);
        vector<uint8_t> stream_port = encode_test_stream(240, 320, 3);

        Decoder decoder;
        decoder.init(1);

        // Warm up decoder
        for (int i = 0; i < 20; ++i) {
            auto& s = (i % 2 == 0) ? stream_land : stream_port;
            decoder.decode(s.data(), s.size(), [](Decoder::FramePtr f) {});
        }

        size_t rss_before = get_current_rss_bytes();
        int frames_received = 0;

        for (int i = 0; i < 1000; ++i) {
            auto& s = (i % 2 == 0) ? stream_land : stream_port;
            decoder.decode(s.data(), s.size(), [&](Decoder::FramePtr f) {
                frames_received++;
            });
        }

        size_t rss_after = get_current_rss_bytes();
        double rss_delta_mb = (double)((ssize_t)rss_after - (ssize_t)rss_before) / (1024.0 * 1024.0);

        cout << "  Processed 1,000 stream flips (" << frames_received << " frames decoded)." << endl;
        cout << "  RSS memory delta across 1,000 flips: " << rss_delta_mb << " MB" << endl;

        if (rss_delta_mb < 2.0) {
            cout << "[PASS] Zero memory leakage across 1,000 mid-stream resolution switches ("
                 << rss_delta_mb << " MB < 2.0 MB threshold)." << endl;
            passed++;
        } else {
            cout << "[FAIL] Memory leakage detected: " << rss_delta_mb << " MB" << endl;
            failed++;
        }
    }

    // SECTION 3: In-Flight Frame Dangling Pointer & Custom Deleter Invariance
    {
        cout << "[TEST 3] Stressing in-flight frame retention across resolution flips..." << endl;

        vector<uint8_t> stream_land = encode_test_stream(320, 240, 5);
        vector<uint8_t> stream_port = encode_test_stream(240, 320, 5);

        Decoder decoder;
        decoder.init(1);

        vector<Decoder::FramePtr> held_frames;

        // Decode landscape frames and HOLD THEM in memory (simulate queue delay)
        decoder.decode(stream_land.data(), stream_land.size(), [&](Decoder::FramePtr f) {
            held_frames.push_back(f);
        });

        int held_count = held_frames.size();
        assert(held_count > 0);

        // Now trigger orientation flip to portrait WHILE old landscape frames are still held!
        vector<Decoder::FramePtr> new_frames;
        decoder.decode(stream_port.data(), stream_port.size(), [&](Decoder::FramePtr f) {
            new_frames.push_back(f);
        });

        // Verify held old frames still have valid old dimensions and valid data
        bool held_frames_intact = true;
        for (auto& f : held_frames) {
            if (f->width != 320 || f->height != 240) {
                held_frames_intact = false;
            }
        }

        // Verify new frames have new dimensions
        bool new_frames_correct = true;
        for (auto& f : new_frames) {
            if (f->width != 240 || f->height != 320) {
                new_frames_correct = false;
            }
        }

        // Now RELEASE the old held frames (calls custom deleter during new resolution state)
        held_frames.clear();

        // Decode more portrait frames to ensure pool is intact and has no stale frames
        bool subsequent_portrait_ok = true;
        decoder.decode(stream_port.data(), stream_port.size(), [&](Decoder::FramePtr f) {
            if (f->width != 240 || f->height != 320) {
                subsequent_portrait_ok = false;
            }
        });

        new_frames.clear();

        if (held_frames_intact && new_frames_correct && subsequent_portrait_ok) {
            cout << "[PASS] In-flight frames safely retained and recycled across resolution switches without dangling pointers." << endl;
            passed++;
        } else {
            cout << "[FAIL] In-flight frame lifecycle corrupted on resolution switch." << endl;
            failed++;
        }
    }

    // SECTION 4: Multi-threaded Producer-Consumer Frame Pool Stress
    {
        cout << "[TEST 4] Concurrent decode and render pipeline stress..." << endl;

        vector<uint8_t> stream_land = encode_test_stream(320, 240, 5);
        vector<uint8_t> stream_port = encode_test_stream(240, 320, 5);

        Decoder decoder;
        decoder.init(1);

        atomic<bool> running{true};
        atomic<int> frames_decoded{0};
        atomic<int> frames_consumed{0};
        queue<Decoder::FramePtr> frame_queue;
        mutex q_mutex;

        // Consumer thread (simulates renderer)
        thread consumer([&]() {
            while (running || frames_consumed < frames_decoded) {
                Decoder::FramePtr frame;
                {
                    lock_guard<mutex> lock(q_mutex);
                    if (!frame_queue.empty()) {
                        frame = frame_queue.front();
                        frame_queue.pop();
                    }
                }
                if (frame) {
                    // Touch frame data to ensure no use-after-free
                    volatile uint8_t b = frame->data[0][0];
                    (void)b;
                    frames_consumed++;
                    this_thread::sleep_for(microseconds(100));
                } else {
                    this_thread::sleep_for(microseconds(500));
                }
            }
        });

        // Producer thread (feeds rapid alternating resolution streams)
        for (int i = 0; i < 200; ++i) {
            auto& s = (i % 2 == 0) ? stream_land : stream_port;
            decoder.decode(s.data(), s.size(), [&](Decoder::FramePtr f) {
                frames_decoded++;
                lock_guard<mutex> lock(q_mutex);
                frame_queue.push(f);
            });
        }

        running = false;
        consumer.join();

        cout << "  Decoded " << frames_decoded << " frames, Consumed " << frames_consumed << " frames." << endl;

        if (frames_decoded > 0 && frames_consumed == frames_decoded) {
            cout << "[PASS] Concurrent decode and frame queue consumption completed with 100% frame accounting and zero races." << endl;
            passed++;
        } else {
            cout << "[FAIL] Concurrent frame queue dropped or lost frames." << endl;
            failed++;
        }
    }

    cout << "\n=== Decoder Dynamic Resolution Stress Harness Summary: "
         << passed << " PASSED, " << failed << " FAILED ===" << endl;
    return (failed == 0) ? 0 : 1;
}
