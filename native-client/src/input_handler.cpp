#include "input_handler.h"
#include "protocol.h"
#include <iostream>

InputHandler::InputHandler(TcpConnection* control_sock, Renderer* renderer)
    : control_sock_(control_sock), renderer_(renderer), is_dragging_(false) {}

bool InputHandler::process_events() {
    SDL_Event event;
    bool pending_motion = false;
    float pending_motion_x = 0;
    float pending_motion_y = 0;

    auto flush_motion = [&]() {
        if (pending_motion) {
            send_touch(2, pending_motion_x, pending_motion_y, 1.0f); // 2=MOVE
            pending_motion = false;
        }
    };

    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_QUIT:
                flush_motion();
                return false;

            case SDL_MOUSEBUTTONDOWN:
            case SDL_MOUSEBUTTONUP: {
                if (event.button.button != SDL_BUTTON_LEFT) break;
                
                flush_motion(); // Send any pending movement before the click/release
                
                float dev_x, dev_y;
                renderer_->window_to_device(event.button.x, event.button.y, dev_x, dev_y);
                
                uint8_t action = (event.type == SDL_MOUSEBUTTONDOWN) ? 0 : 1; // 0=DOWN, 1=UP
                send_touch(action, dev_x, dev_y, 1.0f);
                
                is_dragging_ = (event.type == SDL_MOUSEBUTTONDOWN);
                
                // Track last sent coordinates to implement a deadzone
                last_sent_x_ = event.button.x;
                last_sent_y_ = event.button.y;
                break;
            }

            case SDL_MOUSEMOTION: {
                if (is_dragging_) {
                    // Deadzone filter: ignore microscopic movements (e.g. from Mac trackpads)
                    // that cause Android UI to jitter or "dance".
                    int dx = event.motion.x - last_sent_x_;
                    int dy = event.motion.y - last_sent_y_;
                    if (dx * dx + dy * dy >= 4) { // Move at least 2 pixels
                        renderer_->window_to_device(event.motion.x, event.motion.y, pending_motion_x, pending_motion_y);
                        pending_motion = true;
                        last_sent_x_ = event.motion.x;
                        last_sent_y_ = event.motion.y;
                    }
                }
                break;
            }

            case SDL_MOUSEWHEEL: {
                flush_motion();
                // For trackpad scroll, mouse position doesn't change - use last touch point or center
                int mouse_x, mouse_y;
                if (is_dragging_) {
                    // Use last touch position if dragging
                    mouse_x = last_sent_x_;
                    mouse_y = last_sent_y_;
                } else {
                    // Use current mouse position (for mouse wheel)
                    SDL_GetMouseState(&mouse_x, &mouse_y);
                }
                float dev_x, dev_y;
                renderer_->window_to_device(mouse_x, mouse_y, dev_x, dev_y);
                
                float hscroll = event.wheel.x;
                float vscroll = event.wheel.y;
                if (event.wheel.direction == SDL_MOUSEWHEEL_FLIPPED) {
                    hscroll *= -1;
                    vscroll *= -1;
                }
                send_scroll(dev_x, dev_y, hscroll, vscroll);
                break;
            }

            case SDL_KEYDOWN:
            case SDL_KEYUP: {
                // For text input keys, we need to send repeats on keydown
                // For modifier keys, ignore repeats
                bool is_modifier = (event.key.keysym.sym == SDLK_LCTRL || event.key.keysym.sym == SDLK_RCTRL ||
                                   event.key.keysym.sym == SDLK_LSHIFT || event.key.keysym.sym == SDLK_RSHIFT ||
                                   event.key.keysym.sym == SDLK_LALT || event.key.keysym.sym == SDLK_RALT ||
                                   event.key.keysym.sym == SDLK_LGUI || event.key.keysym.sym == SDLK_RGUI);
                
                if (event.key.repeat && !is_modifier) {
                    // For character keys, send repeat DOWN events for text input
                    if (event.type == SDL_KEYDOWN) {
                        flush_motion();
                        uint32_t keycode = event.key.keysym.sym;
                        send_key(0, keycode); // Repeat DOWN
                    }
                    break;
                }
                
                if (event.key.repeat && is_modifier) {
                    break; // Ignore modifier repeats
                }
                
                flush_motion();
                uint8_t action = (event.type == SDL_KEYDOWN) ? 0 : 1;
                uint32_t keycode = event.key.keysym.sym;
                
                // Very basic key mapping (should be expanded based on Android KeyCodes)
                if (keycode == SDLK_ESCAPE) {
                    keycode = 4; // AKEYCODE_BACK
                }
                
                send_key(action, keycode);
                break;
            }
        }
    }
    
    flush_motion(); // Flush any remaining motion at the end of the event batch
    return true;
}

void InputHandler::send_touch(uint8_t action, float x, float y, float pressure) {
    uint8_t buf[14];
    buf[0] = EVENT_TYPE_TOUCH;
    buf[1] = action;
    write_be_float(buf + 2, x);
    write_be_float(buf + 6, y);
    write_be_float(buf + 10, pressure);
    control_sock_->send_all(buf, sizeof(buf));
}

void InputHandler::send_key(uint8_t action, uint32_t keycode) {
    uint8_t buf[6];
    buf[0] = EVENT_TYPE_KEY;
    buf[1] = action;
    write_be32(buf + 2, keycode);
    control_sock_->send_all(buf, sizeof(buf));
}

void InputHandler::send_scroll(float x, float y, float hscroll, float vscroll) {
    uint8_t buf[17];
    buf[0] = EVENT_TYPE_SCROLL;
    write_be_float(buf + 1, x);
    write_be_float(buf + 5, y);
    write_be_float(buf + 9, hscroll);
    write_be_float(buf + 13, vscroll);
    control_sock_->send_all(buf, sizeof(buf));
}
