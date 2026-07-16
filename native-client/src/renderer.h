#pragma once

#include <SDL2/SDL.h>

extern "C" {
#include <libavcodec/avcodec.h>
}

class Renderer {
public:
    Renderer();
    ~Renderer();

    bool init(int width, int height, const char* title);
    void destroy();
    
    // Updates texture with decoded YUV/NV12 frame and renders it
    void render_frame(AVFrame* frame);
    
    // Convert window coordinates to device coordinates
    void window_to_device(int win_x, int win_y, float& dev_x, float& dev_y);
    
    int get_device_width() const { return device_width_; }
    int get_device_height() const { return device_height_; }

private:
    void reallocate_texture(AVFrame* frame);
    
    SDL_Window* window_;
    SDL_Renderer* renderer_;
    SDL_Texture* texture_;
    
    int device_width_;
    int device_height_;
    int tex_width_;
    int tex_height_;
    int tex_format_;
};
