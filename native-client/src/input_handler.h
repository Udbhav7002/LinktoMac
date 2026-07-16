#pragma once

#include "network.h"
#include "renderer.h"
#include <SDL2/SDL.h>

class InputHandler {
public:
    InputHandler(TcpConnection* control_sock, Renderer* renderer);
    
    // Polls SDL events, serializes to binary protocol, and sends over socket
    // Returns false if the window was closed (SDL_QUIT)
    bool process_events();

private:
    void send_touch(uint8_t action, float x, float y, float pressure);
    void send_key(uint8_t action, uint32_t keycode);
    void send_scroll(float x, float y, float hscroll, float vscroll);

    TcpConnection* control_sock_;
    Renderer* renderer_;
    bool is_dragging_;
    int last_sent_x_ = 0;
    int last_sent_y_ = 0;
};
