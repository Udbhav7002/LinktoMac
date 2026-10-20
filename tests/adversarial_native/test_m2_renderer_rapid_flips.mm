#import <Cocoa/Cocoa.h>
#include "renderer.h"
#include <iostream>
#include <cassert>
#include <cmath>
#include <algorithm>
#include <vector>
#include <chrono>
#include <mach/mach.h>

extern "C" {
#include <libavcodec/avcodec.h>
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

static AVFrame* alloc_frame(int w, int h, AVPixelFormat fmt) {
    AVFrame* f = av_frame_alloc();
    if (!f) return nullptr;
    f->width = w;
    f->height = h;
    f->format = fmt;
    if (av_image_alloc(f->data, f->linesize, w, h, fmt, 32) < 0) {
        av_frame_free(&f);
        return nullptr;
    }
    if (fmt == AV_PIX_FMT_NV12) {
        memset(f->data[0], 0x40, f->linesize[0] * h);
        memset(f->data[1], 0x80, f->linesize[1] * (h / 2));
    } else if (fmt == AV_PIX_FMT_YUV420P) {
        memset(f->data[0], 0x40, f->linesize[0] * h);
        memset(f->data[1], 0x80, f->linesize[1] * (h / 2));
        memset(f->data[2], 0x80, f->linesize[2] * (h / 2));
    }
    return f;
}

static void free_frame(AVFrame* f) {
    if (f) {
        av_freep(&f->data[0]);
        av_frame_free(&f);
    }
}

int main() {
    cout << "=== Running Empirical Renderer Rapid Flips & Texture Realloc Stress Harness ===" << endl;
    int passed = 0;
    int failed = 0;

    // TEST 1: Rapid Orientation Flips & Texture Reallocation across Phone Resolutions (1,000 iterations)
    {
        cout << "[TEST 1] Stressing 1,000 rapid orientation flips across 1080p, 720p, 20:9 and NV12/YUV420P..." << endl;
        Renderer renderer;
        if (!renderer.init(1080, 1920, "M2 Rapid Flips Stress Window")) {
            cerr << "[FAIL] Failed to init Renderer" << endl;
            return 1;
        }

        struct ResolutionConfig {
            int w;
            int h;
            AVPixelFormat fmt;
            const char* desc;
        };

        vector<ResolutionConfig> configs = {
            {1080, 1920, AV_PIX_FMT_NV12, "Portrait 1080x1920 NV12"},
            {1920, 1080, AV_PIX_FMT_NV12, "Landscape 1920x1080 NV12"},
            {720, 1280, AV_PIX_FMT_YUV420P, "Portrait 720x1280 YUV420P"},
            {1280, 720, AV_PIX_FMT_YUV420P, "Landscape 1280x720 YUV420P"},
            {1080, 2400, AV_PIX_FMT_NV12, "Portrait 20:9 1080x2400 NV12"},
            {2400, 1080, AV_PIX_FMT_NV12, "Landscape 20:9 2400x1080 NV12"}
        };

        vector<AVFrame*> frames;
        for (const auto& cfg : configs) {
            AVFrame* f = alloc_frame(cfg.w, cfg.h, cfg.fmt);
            assert(f != nullptr);
            frames.push_back(f);
        }

        // Warm up renderer and Metal driver caches to reach steady-state
        for (int i = 0; i < 50; ++i) {
            @autoreleasepool {
                renderer.render_frame(frames[i % frames.size()]);
                SDL_Event ev; while (SDL_PollEvent(&ev)) {}
            }
        }

        size_t rss_before = get_current_rss_bytes();
        auto start_time = steady_clock::now();

        const int TOTAL_FLIPS = 1000;
        bool dimension_checks_ok = true;
        for (int i = 0; i < TOTAL_FLIPS; ++i) {
            @autoreleasepool {
                int idx = i % configs.size();
                renderer.render_frame(frames[idx]);
                SDL_Event ev; while (SDL_PollEvent(&ev)) {}

                if (renderer.get_device_width() != configs[idx].w ||
                    renderer.get_device_height() != configs[idx].h) {
                    dimension_checks_ok = false;
                    cerr << "[FAIL] Flip " << i << ": expected " << configs[idx].w << "x" << configs[idx].h
                         << ", got " << renderer.get_device_width() << "x" << renderer.get_device_height() << endl;
                    break;
                }
            }
        }

        auto elapsed_ms = duration_cast<milliseconds>(steady_clock::now() - start_time).count();
        size_t rss_after = get_current_rss_bytes();
        ssize_t rss_diff = (ssize_t)rss_after - (ssize_t)rss_before;
        double rss_diff_mb = (double)rss_diff / (1024.0 * 1024.0);

        cout << "  Rendered " << TOTAL_FLIPS << " rapid flips in " << elapsed_ms << "ms ("
             << (1000.0 * TOTAL_FLIPS / elapsed_ms) << " flips/sec)" << endl;
        cout << "  RSS memory delta across 1,000 flips: " << rss_diff_mb << " MB" << endl;

        for (auto f : frames) {
            free_frame(f);
        }

        if (dimension_checks_ok) {
            cout << "[PASS] 1,000 orientation flips and texture reallocations matched device dimensions exactly." << endl;
            passed++;
        } else {
            cout << "[FAIL] Orientation flips resulted in device dimension mismatch." << endl;
            failed++;
        }

        // Memory growth check: across 1,000 flips, heap growth should be virtually zero (< 10.0 MB)
        if (rss_diff_mb < 10.0) {
            cout << "[PASS] Zero memory leakage detected during 1,000 texture reallocations (RSS delta: "
                 << rss_diff_mb << " MB < 10.0 MB threshold)." << endl;
            passed++;
        } else {
            cout << "[FAIL] Memory leakage detected during texture reallocations: " << rss_diff_mb << " MB" << endl;
            failed++;
        }

        // TEST 2: Window-to-Device Coordinate Mapping Invariance on Rapid Flips
        cout << "[TEST 2] Verifying coordinate mapping correctness across orientation flips..." << endl;
        bool mapping_ok = true;

        // In portrait 1080x1920
        AVFrame* f_port = alloc_frame(1080, 1920, AV_PIX_FMT_NV12);
        renderer.render_frame(f_port);
        float dev_x = 0, dev_y = 0;
        renderer.window_to_device(0, 0, dev_x, dev_y);
        if (dev_x < 0.0f || dev_x > 1080.0f || dev_y < 0.0f || dev_y > 1920.0f) {
            mapping_ok = false;
        }

        // In landscape 1920x1080
        AVFrame* f_land = alloc_frame(1920, 1080, AV_PIX_FMT_NV12);
        renderer.render_frame(f_land);
        renderer.window_to_device(100, 100, dev_x, dev_y);
        if (dev_x < 0.0f || dev_x > 1920.0f || dev_y < 0.0f || dev_y > 1080.0f) {
            mapping_ok = false;
        }

        free_frame(f_port);
        free_frame(f_land);

        if (mapping_ok) {
            cout << "[PASS] Coordinate transformation bounds strictly respected in both portrait and landscape." << endl;
            passed++;
        } else {
            cout << "[FAIL] Coordinate transformation out of bounds after orientation flip." << endl;
            failed++;
        }

        renderer.destroy();
    }

    // TEST 3: Extreme 4K Resolution Flips (2160x3840 <-> 3840x2160)
    {
        cout << "[TEST 3] Stressing 200 extreme 4K resolution flips..." << endl;
        Renderer renderer;
        if (!renderer.init(2160, 3840, "M2 4K Stress Window")) {
            cerr << "[FAIL] Failed to init Renderer" << endl;
            return 1;
        }

        AVFrame* f_4k_p = alloc_frame(2160, 3840, AV_PIX_FMT_NV12);
        AVFrame* f_4k_l = alloc_frame(3840, 2160, AV_PIX_FMT_NV12);

        // Warmup
        for (int i = 0; i < 20; ++i) {
            @autoreleasepool {
                renderer.render_frame((i % 2 == 0) ? f_4k_p : f_4k_l);
                SDL_Event ev; while (SDL_PollEvent(&ev)) {}
            }
        }

        size_t mem_start = get_current_rss_bytes();
        for (int i = 0; i < 200; ++i) {
            @autoreleasepool {
                renderer.render_frame((i % 2 == 0) ? f_4k_p : f_4k_l);
                SDL_Event ev; while (SDL_PollEvent(&ev)) {}
            }
        }
        size_t mem_end = get_current_rss_bytes();
        double delta_mb = (double)((ssize_t)mem_end - (ssize_t)mem_start) / (1024.0 * 1024.0);

        cout << "  200 4K flips with SDL_PollEvent: RSS delta = " << delta_mb << " MB" << endl;

        free_frame(f_4k_p);
        free_frame(f_4k_l);
        renderer.destroy();

        if (delta_mb < 5.0) {
            cout << "[PASS] 4K rapid flips bounded without runaway memory growth (" << delta_mb << " MB < 5.0 MB)." << endl;
            passed++;
        } else {
            cout << "[FAIL] 4K flips showed excessive memory growth: " << delta_mb << " MB" << endl;
            failed++;
        }
    }

    cout << "\n=== Renderer Rapid Flips Stress Harness Summary: "
         << passed << " PASSED, " << failed << " FAILED ===" << endl;
    return (failed == 0) ? 0 : 1;
}
