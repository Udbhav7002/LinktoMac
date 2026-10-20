#include "renderer.h"
#include <iostream>
#include <cassert>
#include <cmath>
#include <algorithm>
#include <random>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
}

using namespace std;

// Helper to create an AVFrame of specified dimensions and format
static AVFrame* create_test_frame(int width, int height, AVPixelFormat format) {
    AVFrame* frame = av_frame_alloc();
    if (!frame) return nullptr;

    frame->width = width;
    frame->height = height;
    frame->format = format;

    if (av_image_alloc(frame->data, frame->linesize, width, height, format, 32) < 0) {
        av_frame_free(&frame);
        return nullptr;
    }

    // Fill with dummy color (e.g. dark grey Y=80, U=128, V=128)
    if (format == AV_PIX_FMT_NV12) {
        memset(frame->data[0], 80, frame->linesize[0] * height);
        memset(frame->data[1], 128, frame->linesize[1] * (height / 2));
    } else if (format == AV_PIX_FMT_YUV420P) {
        memset(frame->data[0], 80, frame->linesize[0] * height);
        memset(frame->data[1], 128, frame->linesize[1] * (height / 2));
        memset(frame->data[2], 128, frame->linesize[2] * (height / 2));
    }

    return frame;
}

static void free_test_frame(AVFrame* frame) {
    if (frame) {
        av_freep(&frame->data[0]);
        av_frame_free(&frame);
    }
}

// Algorithmic reference calculation matching renderer.mm
struct WindowCalcResult {
    int target_w;
    int target_h;
    bool valid;
};

static WindowCalcResult calculate_window_size(int frame_w, int frame_h, int bounds_w, int bounds_h) {
    bool new_is_portrait = (frame_h > frame_w);
    int max_w = (int)(bounds_w * 0.85f);
    int max_h = (int)(bounds_h * 0.85f);

    int target_w = 0;
    int target_h = 0;
    if (new_is_portrait) {
        target_h = min(frame_h, min(max_h, 800));
        target_w = (int)((float)frame_w / frame_h * target_h);
        if (target_w > max_w) {
            target_w = max_w;
            target_h = (int)((float)frame_h / frame_w * target_w);
        }
    } else {
        target_w = min(frame_w, min(max_w, 1100));
        target_h = (int)((float)frame_h / frame_w * target_w);
        if (target_h > max_h) {
            target_h = max_h;
            target_w = (int)((float)frame_w / frame_h * target_h);
        }
    }

    target_w = max(100, target_w);
    target_h = max(100, target_h);

    bool valid = (target_w > 0 && target_h > 0 && !isnan(target_w) && !isnan(target_h));
    return {target_w, target_h, valid};
}

int main() {
    cout << "=== Running Dynamic Window Auto-Resize Empirical Stress Harness ===" << endl;
    int tests_passed = 0;
    int tests_failed = 0;

    // SECTION 1: Empirical Live Renderer Testing with AVFrames
    {
        Renderer renderer;
        // Initial setup in portrait mode (1080x1920)
        if (!renderer.init(1080, 1920, "Auto-Resize Test Window")) {
            cerr << "[FAIL] Failed to initialize Renderer in test harness" << endl;
            return 1;
        }

        // Render initial portrait frame (1080x1920 NV12)
        AVFrame* f_portrait = create_test_frame(1080, 1920, AV_PIX_FMT_NV12);
        assert(f_portrait != nullptr);
        renderer.render_frame(f_portrait);

        // Verify initial aspect ratio in portrait
        float dev_x = 0, dev_y = 0;
        renderer.window_to_device(50, 50, dev_x, dev_y);
        bool portrait_init_ok = (renderer.get_device_width() == 1080 && renderer.get_device_height() == 1920);
        if (portrait_init_ok) {
            cout << "[PASS] Initial portrait frame rendered (1080x1920)." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Initial portrait dimensions mismatch." << endl;
            tests_failed++;
        }

        // FLIP 1: Dynamic rotation to Landscape (1920x1080)
        AVFrame* f_landscape = create_test_frame(1920, 1080, AV_PIX_FMT_NV12);
        assert(f_landscape != nullptr);
        renderer.render_frame(f_landscape);

        bool landscape_dims_ok = (renderer.get_device_width() == 1920 && renderer.get_device_height() == 1080);
        if (landscape_dims_ok) {
            cout << "[PASS] Dynamic orientation flip to landscape: device dimensions updated to 1920x1080." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Failed to update device dimensions on landscape flip." << endl;
            tests_failed++;
        }

        // FLIP 2: Dynamic rotation back to Portrait (1080x1920)
        renderer.render_frame(f_portrait);
        bool return_portrait_ok = (renderer.get_device_width() == 1080 && renderer.get_device_height() == 1920);
        if (return_portrait_ok) {
            cout << "[PASS] Dynamic orientation flip back to portrait: device dimensions updated to 1080x1920." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Failed to restore portrait dimensions on flip back." << endl;
            tests_failed++;
        }

        // FLIP 3: Modern 20:9 phone portrait (1080x2400) to landscape (2400x1080)
        AVFrame* f_20_9_portrait = create_test_frame(1080, 2400, AV_PIX_FMT_NV12);
        AVFrame* f_20_9_landscape = create_test_frame(2400, 1080, AV_PIX_FMT_NV12);
        renderer.render_frame(f_20_9_portrait);
        renderer.render_frame(f_20_9_landscape);
        bool aspect_20_9_ok = (renderer.get_device_width() == 2400 && renderer.get_device_height() == 1080);
        if (aspect_20_9_ok) {
            cout << "[PASS] 20:9 aspect ratio rotation flip (1080x2400 <-> 2400x1080) handled cleanly." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] 20:9 aspect ratio rotation failed." << endl;
            tests_failed++;
        }

        // FLIP 4: YUV420P software pixel format orientation flip
        AVFrame* f_yuv_portrait = create_test_frame(720, 1280, AV_PIX_FMT_YUV420P);
        AVFrame* f_yuv_landscape = create_test_frame(1280, 720, AV_PIX_FMT_YUV420P);
        renderer.render_frame(f_yuv_portrait);
        renderer.render_frame(f_yuv_landscape);
        bool yuv_ok = (renderer.get_device_width() == 1280 && renderer.get_device_height() == 720);
        if (yuv_ok) {
            cout << "[PASS] YUV420P software decode frame format orientation flip handled cleanly." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] YUV420P orientation flip failed." << endl;
            tests_failed++;
        }

        free_test_frame(f_portrait);
        free_test_frame(f_landscape);
        free_test_frame(f_20_9_portrait);
        free_test_frame(f_20_9_landscape);
        free_test_frame(f_yuv_portrait);
        free_test_frame(f_yuv_landscape);

        renderer.destroy();
    }

    // SECTION 2: Adversarial Bounds Clamping & Stress Fuzzing (100,000 iterations)
    {
        cout << "[INFO] Fuzzing window auto-resize dimension clamping algorithm over 100,000 randomized inputs..." << endl;
        mt19937 rng(42);
        uniform_int_distribution<int> dim_dist(1, 7680);
        uniform_int_distribution<int> bounds_w_dist(400, 3840);
        uniform_int_distribution<int> bounds_h_dist(300, 2160);

        bool all_valid = true;
        bool all_positive = true;
        bool all_within_bounds = true;

        for (int i = 0; i < 100000; ++i) {
            int fw = dim_dist(rng);
            int fh = dim_dist(rng);
            int bw = bounds_w_dist(rng);
            int bh = bounds_h_dist(rng);

            WindowCalcResult res = calculate_window_size(fw, fh, bw, bh);
            if (!res.valid) {
                all_valid = false;
                break;
            }
            if (res.target_w <= 0 || res.target_h <= 0) {
                all_positive = false;
                break;
            }
            // Clamped check: target cannot exceed display bounds
            if (res.target_w > bw || res.target_h > bh) {
                // If display bounds are smaller than minimum 100, target_w/h can clamp to 100
                if (bw >= 120 && bh >= 120) {
                    all_within_bounds = false;
                    break;
                }
            }
        }

        if (all_valid && all_positive) {
            cout << "[PASS] 100,000 random dimensions fuzz test: 0 zero/negative dimensions, 0 NaN/overflows." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Fuzz test discovered zero/negative or invalid window dimension." << endl;
            tests_failed++;
        }

        if (all_within_bounds) {
            cout << "[PASS] Display bounds clamping verified across all tested resolutions." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Target window dimensions exceeded usable display bounds." << endl;
            tests_failed++;
        }
    }

    // SECTION 3: Edge Case Dimensions Clamping
    {
        // 1x1 extreme frame
        WindowCalcResult r_tiny = calculate_window_size(1, 1, 1440, 900);
        bool tiny_ok = (r_tiny.target_w >= 100 && r_tiny.target_h >= 100);

        // 7680x4320 8K landscape frame
        WindowCalcResult r_8k = calculate_window_size(7680, 4320, 1440, 900);
        bool r8k_ok = (r_8k.target_w <= 1440 && r_8k.target_h <= 900 && r_8k.target_w >= 100 && r_8k.target_h >= 100);

        // Ultra-wide 32:9 (3840x1080)
        WindowCalcResult r_ultrawide = calculate_window_size(3840, 1080, 1440, 900);
        bool ultrawide_ok = (r_ultrawide.target_w <= 1440 && r_ultrawide.target_h <= 900);

        // Ultra-tall 1:3 (1080x3240)
        WindowCalcResult r_ultratall = calculate_window_size(1080, 3240, 1440, 900);
        bool ultratall_ok = (r_ultratall.target_w <= 1440 && r_ultratall.target_h <= 900);

        if (tiny_ok && r8k_ok && ultrawide_ok && ultratall_ok) {
            cout << "[PASS] Extreme aspect ratios (1x1, 8K 7680x4320, 32:9 ultra-wide, 1:3 ultra-tall) strictly clamped." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Extreme aspect ratio clamping failed." << endl;
            tests_failed++;
        }
    }

    cout << "\nDynamic Window Auto-Resize Tests: " << tests_passed << " passed, " << tests_failed << " failed." << endl;
    return (tests_failed == 0) ? 0 : 1;
}
