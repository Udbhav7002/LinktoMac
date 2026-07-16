#include "renderer.h"
#include <iostream>
#include <algorithm>

#ifdef __APPLE__
#import <Cocoa/Cocoa.h>
#include <SDL2/SDL_syswm.h>
#endif

Renderer::Renderer() : 
    window_(nullptr), renderer_(nullptr), texture_(nullptr),
    device_width_(0), device_height_(0), tex_width_(0), tex_height_(0), tex_format_(0) {}

Renderer::~Renderer() {
    destroy();
}

bool Renderer::init(int width, int height, const char* title) {
    device_width_ = width;
    device_height_ = height;

#ifdef __APPLE__
    // Force macOS to treat this background executable as a foreground interactive application
    // so that the window will actually appear and receive focus.
    [NSApplication sharedApplication];
    [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];
    [NSApp activateIgnoringOtherApps:YES];
#endif

    // Set hints BEFORE SDL_Init and SDL_CreateRenderer — they only take effect if set early
    SDL_SetHint(SDL_HINT_RENDER_DRIVER, "metal");
    SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "1");

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS) < 0) {
        std::cerr << "SDL could not initialize! SDL_Error: " << SDL_GetError() << "\n";
        return false;
    }

    // Default window size scaled down if needed to fit typical Mac screens
    int win_h = std::min(height, 800);
    int win_w = (int)((float)width / height * win_h);

    window_ = SDL_CreateWindow(title,
                               SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
                               win_w, win_h,
                               SDL_WINDOW_SHOWN | SDL_WINDOW_RESIZABLE | SDL_WINDOW_ALLOW_HIGHDPI);
    if (!window_) return false;

#ifdef __APPLE__
    SDL_SysWMinfo info;
    SDL_VERSION(&info.version);
    if (SDL_GetWindowWMInfo(window_, &info)) {
        if (info.subsystem == SDL_SYSWM_COCOA) {
            NSWindow *nswin = info.info.cocoa.window;
            // Force the window to maintain the phone's exact aspect ratio during resize
            [nswin setContentAspectRatio:NSMakeSize(width, height)];
            // Disable the maximize/fullscreen button (green traffic light)
            [[nswin standardWindowButton:NSWindowZoomButton] setEnabled:NO];
            // Also prevent native fullscreen just in case
            nswin.collectionBehavior |= NSWindowCollectionBehaviorFullScreenNone;
        }
    }
#endif

    renderer_ = SDL_CreateRenderer(window_, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
    if (!renderer_) return false;

    return true;
}

void Renderer::destroy() {
    if (texture_) { SDL_DestroyTexture(texture_); texture_ = nullptr; }
    if (renderer_) { SDL_DestroyRenderer(renderer_); renderer_ = nullptr; }
    if (window_) { SDL_DestroyWindow(window_); window_ = nullptr; }
    SDL_Quit();
}

void Renderer::reallocate_texture(AVFrame* frame) {
    if (texture_ && tex_width_ == frame->width && tex_height_ == frame->height && tex_format_ == frame->format) {
        return; // Already matched
    }

    if (texture_) {
        // Flush renderer before destroying texture to ensure it's not in use
        SDL_RenderFlush(renderer_);
        SDL_DestroyTexture(texture_);
        texture_ = nullptr;
    }

    Uint32 sdl_format = 0;
    
    // Handle all common HEVC output formats explicitly
    switch (frame->format) {
        case AV_PIX_FMT_NV12:        // VideoToolbox hardware decode (most common on macOS)
        case AV_PIX_FMT_NV21:
            sdl_format = SDL_PIXELFORMAT_NV12;
            break;
        case AV_PIX_FMT_YUV420P:     // Software decode YUV420P (planar)
        case AV_PIX_FMT_YUVJ420P:
            sdl_format = SDL_PIXELFORMAT_IYUV;
            break;
        case AV_PIX_FMT_YUV422P:     // Less common
        case AV_PIX_FMT_YUVJ422P:
            sdl_format = SDL_PIXELFORMAT_IYUV; // SDL doesn't have 422P, fallback
            break;
        case AV_PIX_FMT_YUV444P:
        case AV_PIX_FMT_YUVJ444P:
            sdl_format = SDL_PIXELFORMAT_IYUV; // Fallback
            break;
        case AV_PIX_FMT_VIDEOTOOLBOX: // VideoToolbox opaque - should be converted to NV12 by decoder
            sdl_format = SDL_PIXELFORMAT_NV12;
            break;
        default:
            std::cerr << "Unsupported pixel format: " << frame->format << ", defaulting to IYUV\n";
            sdl_format = SDL_PIXELFORMAT_IYUV;
            break;
    }

    texture_ = SDL_CreateTexture(renderer_, sdl_format, SDL_TEXTUREACCESS_STREAMING, frame->width, frame->height);
    
    if (texture_) {
        tex_width_ = frame->width;
        tex_height_ = frame->height;
        tex_format_ = frame->format;
    } else {
        std::cerr << "Failed to create texture: " << SDL_GetError() << "\n";
    }
}

void Renderer::render_frame(AVFrame* frame) {
    if (!frame || !renderer_) return;

    reallocate_texture(frame);
    if (!texture_) return;

    if (frame->format == AV_PIX_FMT_NV12) {
        SDL_UpdateNVTexture(texture_, nullptr, 
                            frame->data[0], frame->linesize[0], 
                            frame->data[1], frame->linesize[1]);
    } else {
        // Assume YUV420P
        SDL_UpdateYUVTexture(texture_, nullptr,
                             frame->data[0], frame->linesize[0],
                             frame->data[1], frame->linesize[1],
                             frame->data[2], frame->linesize[2]);
    }

    SDL_RenderClear(renderer_);
    
    // Maintain aspect ratio via letterboxing - use renderer output size (pixels, not points) for Retina scaling
    int win_w, win_h;
    SDL_GetRendererOutputSize(renderer_, &win_w, &win_h); // Use renderer size (pixels, not points) for Retina scaling
    
    float screen_aspect = (float)win_w / win_h;
    float frame_aspect = (float)frame->width / frame->height;
    
    SDL_Rect rect;
    if (screen_aspect > frame_aspect) {
        // Window is wider than frame -> pillarbox (bars on sides)
        rect.h = win_h;
        rect.w = (int)(win_h * frame_aspect);
        rect.x = (win_w - rect.w) / 2;
        rect.y = 0;
    } else {
        // Window is taller than frame -> letterbox (bars top/bottom)
        rect.w = win_w;
        rect.h = (int)(win_w / frame_aspect);
        rect.x = 0;
        rect.y = (win_h - rect.h) / 2;
    }

    SDL_RenderCopy(renderer_, texture_, nullptr, &rect);
    SDL_RenderPresent(renderer_);
}

void Renderer::window_to_device(int win_x, int win_y, float& dev_x, float& dev_y) {
    if (!window_) return;

    int win_w, win_h;
    // Use renderer output size (pixels) for correct Retina/High-DPI handling
    SDL_GetRendererOutputSize(renderer_, &win_w, &win_h);

    float screen_aspect = (float)win_w / win_h;
    float frame_aspect = (float)device_width_ / device_height_;
    
    int draw_w, draw_h, draw_x, draw_y;
    
    if (screen_aspect > frame_aspect) {
        draw_h = win_h;
        draw_w = (int)(win_h * frame_aspect);
        draw_x = (win_w - draw_w) / 2;
        draw_y = 0;
    } else {
        draw_w = win_w;
        draw_h = (int)(win_w / frame_aspect);
        draw_x = 0;
        draw_y = (win_h - draw_h) / 2;
    }
    
    // Clamp coordinates to render area
    int mapped_x = std::max(0, std::min(win_x - draw_x, draw_w));
    int mapped_y = std::max(0, std::min(win_y - draw_y, draw_h));
    
    dev_x = (float)mapped_x / draw_w * device_width_;
    dev_y = (float)mapped_y / draw_h * device_height_;
}
