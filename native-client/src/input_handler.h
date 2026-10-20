#pragma once

#include "network.h"
#include "renderer.h"
#include <SDL2/SDL.h>

#include <string>
#include <chrono>

using namespace std;

class InputHandler {
public:
    InputHandler(TcpConnection* control_sock, Renderer* renderer);
    ~InputHandler();
    
    // Polls SDL events, serializes to binary protocol, and sends over socket
    // Returns false if the window was closed (SDL_QUIT)
    bool process_events();

    // Maps SDL navigation/action keysyms to Android KeyEvent codes (returns 0 for printable text keys)
    static uint32_t map_sdl_to_android_keycode(SDL_Keycode sym);

    void send_touch(uint8_t action, float x, float y, float pressure);
    void send_key(uint8_t action, uint32_t keycode);
    void send_scroll(float x, float y, float hscroll, float vscroll);
    void send_text(const string& text);
    void flush_scroll();

private:
    TcpConnection* control_sock_;
    Renderer* renderer_;
    bool is_dragging_;
    int last_sent_x_ = 0;
    int last_sent_y_ = 0;

    // Trackpad scroll coalescing state
    bool has_pending_scroll_ = false;
    float pending_scroll_x_ = 0.0f;
    float pending_scroll_y_ = 0.0f;
    float pending_hscroll_ = 0.0f;
    float pending_vscroll_ = 0.0f;
    std::chrono::steady_clock::time_point last_scroll_sent_time_{};
    std::chrono::steady_clock::time_point last_scroll_event_time_{};
};
